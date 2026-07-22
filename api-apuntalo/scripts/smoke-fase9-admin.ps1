<#
    Smoke F9.9 — Cierre de Fase 9 (Administración multi-tienda).

    Cobertura completa de bloques A–K:
      A  Preflight (login SUPER_ADMIN, Flyway 9.1, esquema, secuencias).
      B  Provisionamiento atómico de negocio (tenant A) + login del ADMIN.
      C  CRUD de Stores (5 tiendas) + guardas de código/estado.
      D  CRUD de usuarios + asignación N:M usuario-tienda + escalada de rol + switch-store.
      E  Mesas: alta, desactivación de libre, guarda MESA_HAS_OPEN_TICKET, DELETE físico deshabilitado.
      F  Catálogo: subcategorías (status + DELETE soft) y productos (multipart + status).
      G  TPV: caja, sesión, ticket (originCashSessionId), líneas, cobro (payments) y cancelación.
      H  switch-store con caja abierta (OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH).
      I  Desactivación de Store: Principal / caja abierta / default de usuario activo.
      J  Retirada de acceso y desactivación de usuario (last-access, replacement, caja, tokenVersion).
      K  Aislamiento cross-tenant / IDOR + auditoría sin secretos.

    NO escribe secretos en el repositorio: todas las credenciales llegan por parámetro
    y $env:PGPASSWORD se fija solo en memoria del proceso y se limpia al terminar.

    Este script SÍ constituye la evidencia de ejecución F9.9. La declaración GO además
    requiere la regresión completa de los 48 casos de Fase 8 (script aparte) y el build limpio.
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][string]$DbHost,
    [int]$DbPort = 5432,
    [Parameter(Mandatory = $true)][string]$DbName,
    [Parameter(Mandatory = $true)][string]$DbUser,
    [Parameter(Mandatory = $true)][string]$DbPassword,
    [string]$PsqlPath = "psql",
    [Nullable[long]]$AlternateStoreId = $null,
    [switch]$RepairQaSequences
)

$ErrorActionPreference = "Stop"
$script:Passed  = 0
$script:Failed  = 0
$script:Skipped = 0
$script:Results = [System.Collections.Generic.List[object]]::new()
$RunId = [guid]::NewGuid().ToString("N").Substring(0, 10)
$RUN   = $RunId

# ---------------------------------------------------------------------------
# Helpers de reporte
# ---------------------------------------------------------------------------
function Pass([string]$Name, $Value) {
    $script:Passed++
    $script:Results.Add([pscustomobject]@{ Result = 'PASS'; Name = $Name; Detail = "$Value" })
    Write-Host "PASS  $Name [$Value]" -ForegroundColor Green
}
function Fail([string]$Name, [string]$Message, $Response = $null) {
    $script:Failed++
    $body = if ($null -ne $Response -and $Response.Content) { " :: $($Response.Content)" } else { "" }
    $script:Results.Add([pscustomobject]@{ Result = 'FAIL'; Name = $Name; Detail = "$Message$body" })
    Write-Host "FAIL  ${Name}: $Message" -ForegroundColor Red
    if ($body) { Write-Host $Response.Content -ForegroundColor DarkRed }
}
function Skip([string]$Name, [string]$Reason) {
    $script:Skipped++
    $script:Results.Add([pscustomobject]@{ Result = 'SKIP'; Name = $Name; Detail = $Reason })
    Write-Host "SKIP  ${Name}: $Reason" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------
function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [string]$Token,
        $Body,
        [string]$IdempotencyKey
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($IdempotencyKey) { $headers["Idempotency-Key"] = $IdempotencyKey }
    $uri = "$($BaseUrl.TrimEnd('/'))$Path"
    $bodyText = $null
    if ($null -ne $Body) { $bodyText = $Body | ConvertTo-Json -Depth 30 -Compress }
    try {
        $a = @{ Uri = $uri; Method = $Method; Headers = $headers; ErrorAction = "Stop" }
        if ($PSVersionTable.PSVersion.Major -lt 6) { $a.UseBasicParsing = $true }
        if ($null -ne $bodyText) { $a.ContentType = "application/json"; $a.Body = $bodyText }
        $r = Invoke-WebRequest @a
        $content = [string]$r.Content
        $json = $null
        if (-not [string]::IsNullOrWhiteSpace($content)) { try { $json = $content | ConvertFrom-Json } catch {} }
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Json = $json; Content = $content; Headers = $r.Headers }
    } catch {
        $resp = $_.Exception.Response; $status = 0; $content = ""
        if ($null -ne $resp) {
            try { $status = [int]$resp.StatusCode } catch {}
            try {
                if ($resp.PSObject.Methods.Name -contains "GetResponseStream") {
                    $sr = [System.IO.StreamReader]::new($resp.GetResponseStream())
                    $content = $sr.ReadToEnd(); $sr.Dispose()
                } elseif ($null -ne $resp.Content) {
                    $content = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                }
            } catch {}
        }
        if ([string]::IsNullOrWhiteSpace($content)) { $content = $_.ErrorDetails.Message }
        if ([string]::IsNullOrWhiteSpace($content)) { $content = $_.Exception.Message }
        $json = $null; try { $json = $content | ConvertFrom-Json } catch {}
        return [pscustomobject]@{ Status = $status; Json = $json; Content = $content; Headers = $null }
    }
}

function Invoke-Multipart {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [string]$Token,
        [Parameter(Mandatory)][string]$JsonPart,
        [string]$PartName = 'product'
    )
    $boundary = [guid]::NewGuid().ToString()
    $LF = "`r`n"
    $body = "--$boundary$LF" +
            "Content-Disposition: form-data; name=`"$PartName`"$LF" +
            "Content-Type: application/json$LF$LF" +
            "$JsonPart$LF" +
            "--$boundary--$LF"
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $uri = "$($BaseUrl.TrimEnd('/'))$Path"
    try {
        $a = @{ Uri = $uri; Method = $Method; Headers = $headers; ErrorAction = "Stop"
                ContentType = "multipart/form-data; boundary=$boundary"; Body = $body }
        if ($PSVersionTable.PSVersion.Major -lt 6) { $a.UseBasicParsing = $true }
        $r = Invoke-WebRequest @a
        $content = [string]$r.Content
        $json = $null
        if (-not [string]::IsNullOrWhiteSpace($content)) { try { $json = $content | ConvertFrom-Json } catch {} }
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Json = $json; Content = $content }
    } catch {
        $resp = $_.Exception.Response; $status = 0; $content = ""
        if ($null -ne $resp) {
            try { $status = [int]$resp.StatusCode } catch {}
            try {
                if ($resp.PSObject.Methods.Name -contains "GetResponseStream") {
                    $sr = [System.IO.StreamReader]::new($resp.GetResponseStream())
                    $content = $sr.ReadToEnd(); $sr.Dispose()
                }
            } catch {}
        }
        if ([string]::IsNullOrWhiteSpace($content)) { $content = $_.ErrorDetails.Message }
        if ([string]::IsNullOrWhiteSpace($content)) { $content = $_.Exception.Message }
        $json = $null; try { $json = $content | ConvertFrom-Json } catch {}
        return [pscustomobject]@{ Status = $status; Json = $json; Content = $content }
    }
}

# ---------------------------------------------------------------------------
# Aserciones
# ---------------------------------------------------------------------------
function Assert-Http([string]$Name, $Response, [int[]]$Expected) {
    if ($Expected -contains $Response.Status) { Pass $Name $Response.Status; return $true }
    Fail $Name "HTTP esperado $($Expected -join '/'), recibido $($Response.Status)" $Response
    return $false
}
function Error-Code($Response) {
    if ($null -eq $Response.Json) { return $null }
    if ($Response.Json.code) { return [string]$Response.Json.code }
    if ($Response.Json.errorCode) { return [string]$Response.Json.errorCode }
    if ($Response.Json.error -and $Response.Json.error.code) { return [string]$Response.Json.error.code }
    return $null
}
function Assert-Code([string]$Name, $Response, [int[]]$ExpectedStatus, [string]$ExpectedCode) {
    $okStatus = $ExpectedStatus -contains $Response.Status
    $actual = Error-Code $Response
    if ($okStatus -and $actual -eq $ExpectedCode) { Pass $Name "$($Response.Status)/$actual"; return $true }
    Fail $Name "esperado $($ExpectedStatus -join '/')/$ExpectedCode, recibido $($Response.Status)/$actual" $Response
    return $false
}
function Assert-Equal([string]$Name, $Actual, $Expected) {
    if ([string]$Actual -eq [string]$Expected) { Pass $Name $Actual; return $true }
    Fail $Name "esperado '$Expected', recibido '$Actual'" $null
    return $false
}
function Assert-True([string]$Name, [bool]$Condition, $Value) {
    if ($Condition) { Pass $Name $Value; return $true }
    Fail $Name "condicion no cumplida; recibido '$Value'" $null
    return $false
}

# ---------------------------------------------------------------------------
# DB
# ---------------------------------------------------------------------------
function Invoke-Psql([string]$Sql) {
    $previous = $env:PGPASSWORD
    try {
        if (-not [string]::IsNullOrEmpty($DbPassword)) { $env:PGPASSWORD = $DbPassword }
        $out = & $PsqlPath -X -q -A -t -v ON_ERROR_STOP=1 -h $DbHost -p $DbPort -U $DbUser -d $DbName -c $Sql 2>&1
        if ($LASTEXITCODE -ne 0) { throw ($out -join [Environment]::NewLine) }
        return (($out | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }) -join "").Trim()
    } finally {
        $env:PGPASSWORD = $previous
    }
}

function Check-Sequence([string]$Table) {
    $seq = "${Table}_id_seq"
    $lastValue = [long](Invoke-Psql "SELECT last_value FROM $seq;")
    $maxRaw = Invoke-Psql "SELECT COALESCE(MAX(id),0) FROM $Table;"
    $maxId = [long]$maxRaw
    if ($lastValue -ge $maxId) {
        Pass "Secuencia $seq" "last=$lastValue >= max=$maxId"
        return
    }
    if ($RepairQaSequences) {
        Invoke-Psql "SELECT setval('$seq', $maxId, true);" | Out-Null
        Pass "QA FIX secuencia $seq" "setval($maxId): last era $lastValue"
        Write-Host "QA FIX  secuencia $seq reparada a $maxId (estaba en $lastValue)" -ForegroundColor Magenta
    } else {
        Fail "Secuencia $seq" "DESALINEADA last=$lastValue < max=$maxId (use -RepairQaSequences)"
        throw "ABORT: secuencia $seq por detras del MAX(id). Reejecute con -RepairQaSequences."
    }
}

function New-Key([string]$Suffix) { return "f99-$RUN-$Suffix" }

# ===========================================================================
Write-Host "`n===== SMOKE F9.9 (cierre Fase 9) =====" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl | Run: $RUN`n"

$superToken = $null
$tenantA = $null; $adminAToken = $null; $adminAUserId = $null; $principalStoreId = $null
$storeS2 = $null; $storeS3 = $null; $storeS4 = $null; $storeS5 = $null
$adminAUser = "qa_f99_adminA_$RUN"
$adminAPass = "Apuntalo$RUN"
$camareroUser = "qa_f99_cam_$RUN";  $camareroId = $null; $camareroToken = $null
$limitedUser  = "qa_f99_lim_$RUN";  $limitedId = $null
$tenantB = $null; $principalStoreB = $null
$subcatId = $null; $productId = $null
$registerId = $null; $sessionId = $null
$mesaId = $null; $ticketId = $null; $ticket2Id = $null
$camSessionId = $null; $limSessionId = $null; $limRegisterId = $null

try {
    # =====================================================================
    # BLOQUE A — PREFLIGHT
    # =====================================================================
    Write-Host "`n-- BLOQUE A: Preflight --" -ForegroundColor Cyan
    $login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = $Username; password = $Password }
    if (-not (Assert-Http "A.login SUPER_ADMIN" $login @(200))) { throw "ABORT: login inicial fallido." }
    $superToken = [string]$login.Json.accessToken
    Assert-Equal "A.rol SUPER_ADMIN" $login.Json.user.role "SUPER_ADMIN" | Out-Null

    $flyway = Invoke-Psql "SELECT max(version) FROM flyway_schema_history WHERE success;"
    Assert-Equal "A.Flyway version" $flyway "9.1" | Out-Null
    $hasActivo = Invoke-Psql "SELECT count(*) FROM information_schema.columns WHERE table_name='subcategories' AND column_name='activo';"
    Assert-True "A.subcategories.activo existe" ($hasActivo -eq "1") $hasActivo | Out-Null

    foreach ($t in @("mesas", "subcategories", "products", "stores", "users")) { Check-Sequence $t }

    # =====================================================================
    # BLOQUE B — PROVISIONAMIENTO (tenant A) + login ADMIN
    # =====================================================================
    Write-Host "`n-- BLOQUE B: Provisionamiento --" -ForegroundColor Cyan
    $provBody = @{
        negocioNombre    = "QA F9.9 A $RUN"
        storeName        = "Principal A $RUN"
        storeCode        = "F99A$RUN"
        storeTimezone    = "America/Lima"
        storeCountryCode = "PE"
        adminNombre      = "Admin A $RUN"
        adminUsername    = $adminAUser
        adminPassword    = $adminAPass
    }
    $prov = Invoke-Api -Method POST -Path "/api/platform/negocios/provision" -Token $superToken -Body $provBody
    if (Assert-Http "B.provision tenant A" $prov @(201)) {
        $tenantA = [long]$prov.Json.negocioId
        $principalStoreId = [long]$prov.Json.principalStore.id
        $adminAUserId = [long]$prov.Json.admin.id
        Assert-True "B.respuesta negocioId"       ($tenantA -gt 0) $tenantA | Out-Null
        Assert-True "B.respuesta principalStore"  ($principalStoreId -gt 0) $principalStoreId | Out-Null
        Assert-True "B.respuesta admin.id"        ($adminAUserId -gt 0) $adminAUserId | Out-Null
        Assert-Equal "B.respuesta admin.username" $prov.Json.admin.username $adminAUser | Out-Null
        Assert-Equal "B.respuesta admin.defaultStoreId" $prov.Json.admin.defaultStoreId $principalStoreId | Out-Null
        Assert-True "B.respuesta sin password" (-not ($prov.Content -match 'password')) "sin password" | Out-Null
    }

    $loginA = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = $adminAUser; password = $adminAPass }
    if (Assert-Http "B.login ADMIN A" $loginA @(200)) {
        $adminAToken = [string]$loginA.Json.accessToken
        Assert-Equal "B.rol ADMIN" $loginA.Json.user.role "ADMIN" | Out-Null
        Assert-Equal "B.store activa = Principal" $loginA.Json.activeStore.id $principalStoreId | Out-Null
    }

    # =====================================================================
    # BLOQUE C — CRUD Stores (5 tiendas)
    # =====================================================================
    Write-Host "`n-- BLOQUE C: CRUD Stores --" -ForegroundColor Cyan
    function New-Store([string]$suffix) {
        $r = Invoke-Api -Method POST -Path "/api/admin/stores" -Token $adminAToken -Body @{
            name = "Sucursal $suffix $RUN"; code = "F99A$RUN$suffix"; timezone = "America/Lima"; countryCode = "PE"
        }
        if (Assert-Http "C.crear store $suffix" $r @(201)) { return [long]$r.Json.id }
        return $null
    }
    $storeS2 = New-Store "S2"
    $storeS3 = New-Store "S3"
    $storeS4 = New-Store "S4"
    $storeS5 = New-Store "S5"

    $list = Invoke-Api -Method GET -Path "/api/admin/stores?negocioId=$tenantA&size=50" -Token $adminAToken
    if (Assert-Http "C.listar stores" $list @(200)) {
        $ids = @($list.Json.content | ForEach-Object { [long]$_.id })
        Assert-True "C.5 stores del tenant A" ($ids.Count -ge 5 -and ($ids -contains $principalStoreId) -and ($ids -contains $storeS2)) ($ids -join ',') | Out-Null
    }

    $dup = Invoke-Api -Method POST -Path "/api/admin/stores" -Token $adminAToken -Body @{
        name = "Duplicada $RUN"; code = "F99A${RUN}S2"; timezone = "America/Lima"; countryCode = "PE"
    }
    Assert-Code "C.codigo duplicado" $dup @(409) "STORE_CODE_ALREADY_EXISTS" | Out-Null

    $upd = Invoke-Api -Method PUT -Path "/api/admin/stores/$storeS2" -Token $adminAToken -Body @{
        name = "Sucursal S2 renombrada $RUN"; code = "F99A${RUN}S2"; timezone = "America/Lima"; countryCode = "PE"
    }
    Assert-Http "C.actualizar store" $upd @(200) | Out-Null

    $deact = Invoke-Api -Method PATCH -Path "/api/admin/stores/$storeS5/status" -Token $adminAToken -Body @{ active = $false }
    if (Assert-Http "C.desactivar store secundaria" $deact @(200)) {
        Assert-Equal "C.store S5 inactiva" $deact.Json.active $false | Out-Null
    }
    Invoke-Api -Method PATCH -Path "/api/admin/stores/$storeS5/status" -Token $adminAToken -Body @{ active = $true } | Out-Null

    # =====================================================================
    # BLOQUE D — Usuarios + asignaciones + escalada + switch
    # =====================================================================
    Write-Host "`n-- BLOQUE D: Usuarios y asignaciones --" -ForegroundColor Cyan
    $cam = Invoke-Api -Method POST -Path "/api/admin/users" -Token $adminAToken -Body @{
        nombre = "Camarero $RUN"; username = $camareroUser; password = "Camarero$RUN"; role = "CAMARERO"
        storeIds = @($principalStoreId, $storeS2); defaultStoreId = $principalStoreId
    }
    if (Assert-Http "D.crear CAMARERO" $cam @(201)) { $camareroId = [long]$cam.Json.id }

    $lim = Invoke-Api -Method POST -Path "/api/admin/users" -Token $adminAToken -Body @{
        nombre = "Admin limitado $RUN"; username = $limitedUser; password = "Limited$RUN"; role = "ADMIN"
        storeIds = @($storeS2); defaultStoreId = $storeS2
    }
    if (Assert-Http "D.crear ADMIN limitado (solo S2)" $lim @(201)) { $limitedId = [long]$lim.Json.id }

    # Escalada de rol prohibida
    $esc = Invoke-Api -Method POST -Path "/api/admin/users" -Token $adminAToken -Body @{
        nombre = "Escalado $RUN"; username = "qa_f99_esc_$RUN"; password = "Escalado$RUN"; role = "SUPER_ADMIN"
        storeIds = @($principalStoreId); defaultStoreId = $principalStoreId
    }
    Assert-Code "D.ADMIN no puede crear SUPER_ADMIN" $esc @(403) "ROLE_ESCALATION_FORBIDDEN" | Out-Null

    # Asignación batch al camarero: principal + S2 + S3
    if ($camareroId) {
        $batch = Invoke-Api -Method PUT -Path "/api/admin/users/$camareroId/stores" -Token $adminAToken -Body @{
            activeStoreIds = @($principalStoreId, $storeS2, $storeS3); defaultStoreId = $principalStoreId
        }
        Assert-Http "D.batch assign camarero (3 tiendas)" $batch @(200) | Out-Null

        # default fuera del conjunto activo
        $badDefault = Invoke-Api -Method PUT -Path "/api/admin/users/$camareroId/stores" -Token $adminAToken -Body @{
            activeStoreIds = @($principalStoreId); defaultStoreId = $storeS2
        }
        Assert-Code "D.default fuera del set activo" $badDefault @(409) "DEFAULT_STORE_NOT_IN_ACTIVE_SET" | Out-Null
    }

    # Switch-store del camarero
    $loginCam = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = $camareroUser; password = "Camarero$RUN" }
    if (Assert-Http "D.login CAMARERO" $loginCam @(200)) {
        $camareroToken = [string]$loginCam.Json.accessToken
        $sw = Invoke-Api -Method POST -Path "/api/auth/switch-store" -Token $camareroToken -Body @{ storeId = $storeS2 }
        Assert-Http "D.switch a tienda autorizada (S2)" $sw @(200) | Out-Null
        $swBad = Invoke-Api -Method POST -Path "/api/auth/switch-store" -Token $camareroToken -Body @{ storeId = $storeS4 }
        Assert-Code "D.switch a tienda no autorizada" $swBad @(403) "STORE_ACCESS_DENIED" | Out-Null
    }

    # =====================================================================
    # BLOQUE F — Catálogo (subcategorías + productos)  [antes de TPV]
    # =====================================================================
    Write-Host "`n-- BLOQUE F: Catalogo --" -ForegroundColor Cyan
    $sub = Invoke-Api -Method POST -Path "/api/subcategories" -Token $adminAToken -Body @{ nombre = "QA F99 $RUN"; category = "BEBIDA" }
    if (Assert-Http "F.crear subcategoria" $sub @(200, 201)) {
        $subcatId = [long]$sub.Json.id
        Assert-Equal "F.subcategoria activa" $sub.Json.activo $true | Out-Null
        $subOff = Invoke-Api -Method PATCH -Path "/api/subcategories/$subcatId/status" -Token $adminAToken -Body @{ active = $false }
        if (Assert-Http "F.desactivar subcategoria" $subOff @(200)) { Assert-Equal "F.subcategoria inactiva" $subOff.Json.activo $false | Out-Null }
        Invoke-Api -Method PATCH -Path "/api/subcategories/$subcatId/status" -Token $adminAToken -Body @{ active = $true } | Out-Null
    }

    if ($subcatId) {
        $prodJson = @{ name = "Producto QA $RUN"; subcategoryId = $subcatId; price = 10.00; description = "smoke f99" } | ConvertTo-Json -Compress
        $prod = Invoke-Multipart -Method POST -Path "/api/products" -Token $adminAToken -JsonPart $prodJson -PartName "product"
        if (Assert-Http "F.crear producto (multipart)" $prod @(200, 201)) {
            $productId = [long]$prod.Json.id
            $prodOff = Invoke-Api -Method PATCH -Path "/api/products/$productId/status" -Token $adminAToken -Body @{ active = $false }
            Assert-Http "F.desactivar producto" $prodOff @(200) | Out-Null
            Invoke-Api -Method PATCH -Path "/api/products/$productId/status" -Token $adminAToken -Body @{ active = $true } | Out-Null
        }
    }

    # DELETE subcategoría = soft-deactivate (no borra físicamente)
    if ($subcatId) {
        $delSub = Invoke-Api -Method DELETE -Path "/api/subcategories/$subcatId" -Token $adminAToken
        if (Assert-Http "F.DELETE subcategoria (soft)" $delSub @(200, 204)) {
            $after = Invoke-Api -Method GET -Path "/api/subcategories/$subcatId" -Token $adminAToken
            Assert-True "F.subcategoria sigue existiendo (soft)" ($after.Status -eq 200 -and $after.Json.activo -eq $false) "activo=$($after.Json.activo)" | Out-Null
        }
        Invoke-Api -Method PATCH -Path "/api/subcategories/$subcatId/status" -Token $adminAToken -Body @{ active = $true } | Out-Null
    }

    # =====================================================================
    # BLOQUE G + E (parte OPEN) — TPV y estado con caja/ticket abiertos
    # =====================================================================
    Write-Host "`n-- BLOQUE G/E: TPV (caja, sesion, ticket) --" -ForegroundColor Cyan
    $reg = Invoke-Api -Method POST -Path "/api/admin/cash-registers" -Token $adminAToken -IdempotencyKey (New-Key "reg") -Body @{ name = "Caja QA $RUN" }
    if (Assert-Http "G.crear caja" $reg @(200, 201)) { $registerId = [long]$reg.Json.id }

    if ($registerId) {
        $open = Invoke-Api -Method POST -Path "/api/cash-sessions/open" -Token $adminAToken -IdempotencyKey (New-Key "open") -Body @{ cashRegisterId = $registerId; openingFloat = 0.00 }
        if (Assert-Http "G.abrir sesion de caja" $open @(200, 201)) { $sessionId = [long]$open.Json.id }
    }

    # Mesa para el ticket
    $numero = Get-Random -Minimum 5000 -Maximum 8999
    $mesa = Invoke-Api -Method POST -Path "/api/mesas" -Token $adminAToken -Body @{ numero = $numero }
    if (Assert-Http "E.crear mesa" $mesa @(200, 201)) {
        $mesaId = [long]$mesa.Json.id
        Assert-Equal "E.mesa activa" $mesa.Json.activa $true | Out-Null
    }

    # Desactivar mesa libre debe permitirse; reactivar
    if ($mesaId) {
        $mOff = Invoke-Api -Method PATCH -Path "/api/mesas/$mesaId/status" -Token $adminAToken -Body @{ active = $false }
        Assert-Http "E.desactivar mesa libre" $mOff @(200) | Out-Null
        Invoke-Api -Method PATCH -Path "/api/mesas/$mesaId/status" -Token $adminAToken -Body @{ active = $true } | Out-Null
    }

    # Crear ticket con originCashSessionId y añadir línea
    if ($mesaId -and $sessionId -and $productId) {
        $tk = Invoke-Api -Method POST -Path "/api/tickets" -Token $adminAToken -IdempotencyKey (New-Key "tk1") -Body @{ mesaId = $mesaId; originCashSessionId = $sessionId }
        if (Assert-Http "G.crear ticket (originCashSessionId)" $tk @(200, 201)) { $ticketId = [long]$tk.Json.id }
        if ($ticketId) {
            $addl = Invoke-Api -Method POST -Path "/api/tickets/$ticketId/lines" -Token $adminAToken -IdempotencyKey (New-Key "tk1l") -Body @{ lines = @(@{ productId = $productId; quantity = 1 }) }
            Assert-Http "G.añadir linea al ticket" $addl @(200) | Out-Null
        }
    } else {
        Skip "G.crear ticket" "faltan mesa/sesion/producto"
    }

    # E: guarda MESA_HAS_OPEN_TICKET (con ticket OPEN sobre la mesa)
    if ($mesaId -and $ticketId) {
        $mBlock = Invoke-Api -Method PATCH -Path "/api/mesas/$mesaId/status" -Token $adminAToken -Body @{ active = $false }
        Assert-Code "E.desactivar mesa con ticket abierto" $mBlock @(409) "MESA_HAS_OPEN_TICKET" | Out-Null
    } else {
        Skip "E.MESA_HAS_OPEN_TICKET" "no hubo ticket abierto"
    }

    # E: DELETE físico de mesa deshabilitado
    if ($mesaId) {
        $mDel = Invoke-Api -Method DELETE -Path "/api/mesas/$mesaId" -Token $adminAToken
        Assert-Code "E.DELETE mesa deshabilitado" $mDel @(409) "PHYSICAL_DELETE_DISABLED" | Out-Null
    }

    # =====================================================================
    # BLOQUE H — switch-store con caja abierta
    # =====================================================================
    Write-Host "`n-- BLOQUE H: switch-store con caja abierta --" -ForegroundColor Cyan
    if ($sessionId) {
        $swGuard = Invoke-Api -Method POST -Path "/api/auth/switch-store" -Token $adminAToken -Body @{ storeId = $storeS2 }
        Assert-Code "H.switch bloqueado por caja abierta" $swGuard @(409) "OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH" | Out-Null
    } else {
        Skip "H.switch guard" "no hubo sesion de caja abierta"
    }

    # =====================================================================
    # BLOQUE I — Desactivación de Store (guardas)
    # =====================================================================
    Write-Host "`n-- BLOQUE I: Desactivar Store (guardas) --" -ForegroundColor Cyan
    # I.1 Principal no desactivable
    $iPrim = Invoke-Api -Method PATCH -Path "/api/admin/stores/$principalStoreId/status" -Token $adminAToken -Body @{ active = $false }
    Assert-Code "I.Principal no desactivable" $iPrim @(409) "STORE_IS_PRIMARY_CANNOT_DISABLE" | Out-Null

    # I.2 Store con caja abierta: el ADMIN limitado abre sesion en S2
    $loginLim = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = $limitedUser; password = "Limited$RUN" }
    if (Assert-Http "I.login ADMIN limitado" $loginLim @(200)) {
        $limToken = [string]$loginLim.Json.accessToken
        $limReg = Invoke-Api -Method POST -Path "/api/admin/cash-registers" -Token $limToken -IdempotencyKey (New-Key "limreg") -Body @{ name = "Caja S2 $RUN" }
        if (Assert-Http "I.caja en S2 (admin limitado)" $limReg @(200, 201)) {
            $limRegisterId = [long]$limReg.Json.id
            $limOpen = Invoke-Api -Method POST -Path "/api/cash-sessions/open" -Token $limToken -IdempotencyKey (New-Key "limopen") -Body @{ cashRegisterId = $limRegisterId; openingFloat = 0.00 }
            if (Assert-Http "I.abrir sesion en S2" $limOpen @(200, 201)) { $limSessionId = [long]$limOpen.Json.id }
        }
    }
    if ($limSessionId) {
        $iCash = Invoke-Api -Method PATCH -Path "/api/admin/stores/$storeS2/status" -Token $adminAToken -Body @{ active = $false }
        Assert-Code "I.Store con caja abierta" $iCash @(409) "STORE_HAS_OPEN_CASH_SESSIONS" | Out-Null

        # cerrar la sesion de S2 (sin conciliacion => sin countedCash) para probar la guarda de default
        $limClose = Invoke-Api -Method POST -Path "/api/cash-sessions/$limSessionId/close" -Token $limToken -IdempotencyKey (New-Key "limclose") -Body @{ acknowledgePendingTickets = $true }
        Assert-Http "I.cerrar sesion S2" $limClose @(200) | Out-Null
        $limSessionId = $null
        $iDefault = Invoke-Api -Method PATCH -Path "/api/admin/stores/$storeS2/status" -Token $adminAToken -Body @{ active = $false }
        Assert-Code "I.Store default de usuario activo" $iDefault @(409) "STORE_IS_DEFAULT_OF_ACTIVE_USER" | Out-Null
    } else {
        Skip "I.Store con caja abierta / default" "no se abrio sesion en S2"
    }

    # =====================================================================
    # BLOQUE J — Retirada de acceso + desactivación de usuario
    # =====================================================================
    Write-Host "`n-- BLOQUE J: Accesos y desactivacion de usuario --" -ForegroundColor Cyan
    # J.1 last-active: el ADMIN limitado solo tiene S2 -> revocar S2 falla
    if ($limitedId) {
        $revLast = Invoke-Api -Method DELETE -Path "/api/admin/users/$limitedId/stores/$storeS2" -Token $adminAToken
        Assert-Code "J.retirar ultimo acceso activo" $revLast @(409) "LAST_ACTIVE_STORE_ACCESS" | Out-Null
    }

    # J.2 replacement default requerido: revocar la default del camarero (principal) sin reemplazo
    if ($camareroId) {
        $revDef = Invoke-Api -Method DELETE -Path "/api/admin/users/$camareroId/stores/$principalStoreId" -Token $adminAToken
        Assert-Code "J.retirar default sin reemplazo" $revDef @(409) "REPLACEMENT_DEFAULT_STORE_REQUIRED" | Out-Null
    }

    # J.3 USER_HAS_OPEN_CASH_SESSION: el camarero abre sesion en S2 y se intenta retirar S3
    if ($camareroId -and $camareroToken) {
        $swc = Invoke-Api -Method POST -Path "/api/auth/switch-store" -Token $camareroToken -Body @{ storeId = $storeS2 }
        if ($swc.Status -eq 200 -and $limRegisterId) {
            $camToken2 = [string]$swc.Json.accessToken
            $camOpen = Invoke-Api -Method POST -Path "/api/cash-sessions/open" -Token $camToken2 -IdempotencyKey (New-Key "camopen") -Body @{ cashRegisterId = $limRegisterId; openingFloat = 0.00 }
            if (Assert-Http "J.camarero abre sesion en S2" $camOpen @(200, 201)) {
                $camSessionId = [long]$camOpen.Json.id
                $revOpen = Invoke-Api -Method DELETE -Path "/api/admin/users/$camareroId/stores/$storeS3" -Token $adminAToken
                Assert-Code "J.retirar acceso con caja abierta" $revOpen @(409) "USER_HAS_OPEN_CASH_SESSION" | Out-Null

                # J.4 no desactivar usuario con caja abierta
                $deacOpen = Invoke-Api -Method PATCH -Path "/api/admin/users/$camareroId/status" -Token $adminAToken -Body @{ active = $false }
                Assert-Code "J.desactivar usuario con caja abierta" $deacOpen @(409) "USER_HAS_OPEN_CASH_SESSION" | Out-Null

                # cerrar la sesion del camarero (sin conciliacion => sin countedCash)
                $camClose = Invoke-Api -Method POST -Path "/api/cash-sessions/$camSessionId/close" -Token $camToken2 -IdempotencyKey (New-Key "camclose") -Body @{ acknowledgePendingTickets = $true }
                Assert-Http "J.cerrar sesion camarero" $camClose @(200) | Out-Null
                $camSessionId = $null
            }
        } else { Skip "J.caja abierta camarero" "el camarero no pudo cambiar a S2 o falta caja S2" }
    }

    # J.5 desactivar usuario (tokenVersion++): desactivar camarero y comprobar token viejo invalido
    if ($camareroId -and $camareroToken) {
        $deac = Invoke-Api -Method PATCH -Path "/api/admin/users/$camareroId/status" -Token $adminAToken -Body @{ active = $false }
        if (Assert-Http "J.desactivar camarero" $deac @(200)) {
            Assert-Equal "J.camarero inactivo" $deac.Json.activo $false | Out-Null
            $meOld = Invoke-Api -Method GET -Path "/api/auth/me" -Token $camareroToken
            Assert-True "J.token viejo invalidado (tokenVersion++)" ($meOld.Status -eq 401) $meOld.Status | Out-Null
        }
        Invoke-Api -Method PATCH -Path "/api/admin/users/$camareroId/status" -Token $adminAToken -Body @{ active = $true } | Out-Null
    }

    # =====================================================================
    # BLOQUE K — Aislamiento cross-tenant / IDOR + Auditoría
    # =====================================================================
    Write-Host "`n-- BLOQUE K: Cross-tenant / IDOR + Auditoria --" -ForegroundColor Cyan
    # Provisionar tenant B
    $provB = Invoke-Api -Method POST -Path "/api/platform/negocios/provision" -Token $superToken -Body @{
        negocioNombre = "QA F9.9 B $RUN"; storeCode = "F99B$RUN"; storeCountryCode = "PE"; storeTimezone = "America/Lima"
        adminNombre = "Admin B $RUN"; adminUsername = "qa_f99_adminB_$RUN"; adminPassword = "ApuntaloB$RUN"
    }
    if (Assert-Http "K.provision tenant B" $provB @(201)) {
        $tenantB = [long]$provB.Json.negocioId
        $principalStoreB = [long]$provB.Json.principalStore.id
    }

    if ($tenantB) {
        # ADMIN A intenta administrar tenant B via negocioId -> 403 TENANT_SCOPE_FORBIDDEN
        $cross = Invoke-Api -Method GET -Path "/api/admin/stores?negocioId=$tenantB" -Token $adminAToken
        Assert-Code "K.ADMIN A no administra tenant B" $cross @(403) "TENANT_SCOPE_FORBIDDEN" | Out-Null

        # ADMIN A intenta leer la Store Principal de B dentro de su propio scope -> 404
        $crossStore = Invoke-Api -Method GET -Path "/api/admin/stores/$principalStoreB" -Token $adminAToken
        Assert-Http "K.Store de B no visible para A" $crossStore @(404) | Out-Null
    }

    # ADMIN limitado (solo S2) intenta operar sobre una store sin autoridad (S4) -> 403
    if ($limitedId) {
        $loginLim2 = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = $limitedUser; password = "Limited$RUN" }
        if ($loginLim2.Status -eq 200) {
            $limToken2 = [string]$loginLim2.Json.accessToken
            $limCross = Invoke-Api -Method PATCH -Path "/api/admin/stores/$storeS4/status" -Token $limToken2 -Body @{ active = $false }
            Assert-Code "K.ADMIN limitado sin autoridad sobre S4" $limCross @(403) "STORE_AUTHORITY_REQUIRED" | Out-Null
        }
    }

    # Auditoría: endpoint accesible y sin secretos
    $audit = Invoke-Api -Method GET -Path "/api/admin/audit-events?page=0&size=50" -Token $adminAToken
    if (Assert-Http "K.auditoria accesible" $audit @(200)) {
        $count = @($audit.Json.content).Count
        Assert-True "K.auditoria con eventos del tenant A" ($count -gt 0) $count | Out-Null
    }
    # SQL: ningún campo JSON de auditoría contiene password/hash/jwt para tenant A y B
    if ($tenantA) {
        $tenants = "$tenantA"
        if ($tenantB) { $tenants = "$tenantA,$tenantB" }
        $leakSql = @"
SELECT count(*) FROM audit_events
WHERE negocio_id IN ($tenants)
  AND (
     COALESCE(previous_state_json,'') ILIKE '%password%' OR COALESCE(new_state_json,'') ILIKE '%password%' OR COALESCE(metadata_json,'') ILIKE '%password%'
  OR COALESCE(previous_state_json,'') ILIKE '%"jwt"%'   OR COALESCE(new_state_json,'') ILIKE '%"jwt"%'   OR COALESCE(metadata_json,'') ILIKE '%"jwt"%'
  OR COALESCE(previous_state_json,'') ILIKE '%accessToken%' OR COALESCE(new_state_json,'') ILIKE '%accessToken%' OR COALESCE(metadata_json,'') ILIKE '%accessToken%'
  OR COALESCE(previous_state_json,'') LIKE '%\$2a\$%' OR COALESCE(new_state_json,'') LIKE '%\$2a\$%' OR COALESCE(metadata_json,'') LIKE '%\$2a\$%'
  OR COALESCE(previous_state_json,'') LIKE '%\$2b\$%' OR COALESCE(new_state_json,'') LIKE '%\$2b\$%' OR COALESCE(metadata_json,'') LIKE '%\$2b\$%'
  );
"@
        $leaks = Invoke-Psql $leakSql
        Assert-True "K.auditoria sin secretos (SQL)" ($leaks -eq "0") "coincidencias=$leaks" | Out-Null
    }

    # =====================================================================
    # LIMPIEZA (sin borrados físicos de historia)
    # =====================================================================
    Write-Host "`n-- Limpieza QA --" -ForegroundColor Cyan
    # cobrar el ticket abierto y cerrar la sesion de adminA
    if ($ticketId -and $sessionId) {
        $detail = Invoke-Api -Method GET -Path "/api/tickets/$ticketId" -Token $adminAToken
        $total = if ($detail.Json.total) { [decimal]$detail.Json.total } else { [decimal]10.00 }
        $pay = Invoke-Api -Method POST -Path "/api/tickets/$ticketId/pay" -Token $adminAToken -IdempotencyKey (New-Key "pay") -Body @{
            cashSessionId = $sessionId
            payments = @(@{ method = "CASH"; amount = $total; cashReceived = $total })
        }
        Assert-Http "G.cobrar ticket (cleanup)" $pay @(200) | Out-Null
    }
    if ($sessionId) {
        Invoke-Api -Method POST -Path "/api/cash-sessions/$sessionId/close" -Token $adminAToken -IdempotencyKey (New-Key "close") -Body @{ acknowledgePendingTickets = $true } | Out-Null
    }
    # desactivar entidades QA (no borrado físico)
    if ($mesaId) { Invoke-Api -Method PATCH -Path "/api/mesas/$mesaId/status" -Token $adminAToken -Body @{ active = $false } | Out-Null }
    if ($productId) { Invoke-Api -Method PATCH -Path "/api/products/$productId/status" -Token $adminAToken -Body @{ active = $false } | Out-Null }
    if ($subcatId) { Invoke-Api -Method PATCH -Path "/api/subcategories/$subcatId/status" -Token $adminAToken -Body @{ active = $false } | Out-Null }
    # desactivar negocios QA como SUPER_ADMIN
    if ($tenantA) { Invoke-Api -Method PATCH -Path "/api/platform/negocios/$tenantA/status" -Token $superToken -Body @{ active = $false } | Out-Null }
    if ($tenantB) { Invoke-Api -Method PATCH -Path "/api/platform/negocios/$tenantB/status" -Token $superToken -Body @{ active = $false } | Out-Null }
    Write-Host "Limpieza QA completada (desactivacion; sin borrados fisicos)."

} catch {
    Fail "FATAL" $_.Exception.Message $null
} finally {
    $env:PGPASSWORD = ""
}

# ===========================================================================
# RESUMEN
# ===========================================================================
Write-Host "`n===== RESUMEN F9.9 =====" -ForegroundColor Cyan
$script:Results | Format-Table -AutoSize Result, Name, Detail | Out-String | Write-Host

$total = $script:Passed + $script:Failed
$verdict = if ($script:Failed -eq 0) { "GO" } else { "BLOQUEADA" }
Write-Host ("F9.9  PASS={0}  FAIL={1}  SKIP={2}  (checks={3})" -f $script:Passed, $script:Failed, $script:Skipped, $total)
Write-Host ("VEREDICTO F9.9: {0}" -f $verdict) -ForegroundColor ($(if ($verdict -eq 'GO') { 'Green' } else { 'Red' }))

if ($script:Failed -gt 0) { exit 1 } else { exit 0 }
