param(
    [string]$BaseUrl = "http://localhost:18080",
    [string]$Username = "harbey",
    [Parameter(Mandatory = $true)][string]$Password
)

$ErrorActionPreference = "Stop"
$results = [System.Collections.Generic.List[object]]::new()

function Assert-ScalarLong {
    param([object]$Value, [string]$Name)
    if ($null -eq $Value) { throw "$Name es null." }
    if ($Value -is [System.Array]) { throw "$Name no puede ser un array." }
    try { return [long]$Value } catch { throw "$Name no es un Long válido." }
}

function Assert-HttpStatus {
    param([int]$Actual, [int[]]$Expected, [string]$Step, [string]$ResponseBody)
    if ($Expected -notcontains $Actual) {
        throw "[$Step] HTTP $Actual. Esperado: $($Expected -join ','). Body: $ResponseBody"
    }
}

function New-Key { [guid]::NewGuid().ToString() }

function Invoke-Checked {
    param([string]$Step,[string]$Method,[string]$Path,[hashtable]$Headers,[object]$Body,[int[]]$Expected)
    $json = if ($null -ne $Body) { $Body | ConvertTo-Json -Depth 10 -Compress } else { $null }
    try {
        $args = @{ Method=$Method; Uri="$BaseUrl$Path"; Headers=$Headers; UseBasicParsing=$true }
        if ($null -ne $json) { $args.ContentType='application/json'; $args.Body=$json }
        $response = Invoke-WebRequest @args
        $status = [int]$response.StatusCode; $content = $response.Content
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        $content = $_.ErrorDetails.Message
    }
    Assert-HttpStatus $status $Expected $Step $content
    $results.Add([pscustomobject]@{ Step=$Step; HTTP=$status; Result='PASS' })
    [pscustomobject]@{ Status=$status; Body=if($content){$content|ConvertFrom-Json}else{$null}; Headers=$response.Headers }
}

$login = Invoke-Checked 'Login' POST '/api/auth/login' @{} @{username=$Username;password=$Password} @(200)
$auth = @{ Authorization = "Bearer $($login.Body.accessToken)" }
$products = (Invoke-Checked 'Productos activos' GET '/api/products/activos' $auth $null @(200)).Body
$mesas = (Invoke-Checked 'Mesas' GET '/api/mesas' $auth $null @(200)).Body
$product = @($products | Where-Object activo)[0]
$free = @($mesas | Where-Object { $_.activa -eq $true -and $_.status -eq 'FREE' })
if ($null -eq $product -or $free.Count -lt 2) { throw 'Faltan producto activo o dos mesas libres.' }
$script:ProductId = Assert-ScalarLong $product.id 'productId'
$script:MesaA = Assert-ScalarLong $free[0].id 'mesaAId'
$script:MesaB = Assert-ScalarLong $free[1].id 'mesaBId'

# Verificación defensiva del bug que motivó este runner.
$probe = @{mesaId=$script:MesaA;notes='QA_PHASES_2_4_PROBE'} | ConvertTo-Json -Compress | ConvertFrom-Json
if ($probe.mesaId -is [System.Array]) { throw 'mesaId se serializó como array.' }

throw 'Runner incompleto: la matriz de Fases 2-4 todavía no está implementada; no se emiten falsos PASS.'
