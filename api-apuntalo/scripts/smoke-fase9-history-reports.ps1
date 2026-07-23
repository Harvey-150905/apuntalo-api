[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$StoreAToken = $env:F9_HISTORY_STORE_A_TOKEN,
    [string]$StoreBToken = $env:F9_HISTORY_STORE_B_TOKEN,
    [Parameter(Mandatory = $true)][long]$CashTicketId,
    [Parameter(Mandatory = $true)][long]$CardTicketId,
    [Parameter(Mandatory = $true)][long]$MixedTicketId,
    [Parameter(Mandatory = $true)][long]$PaidByUserId,
    [Parameter(Mandatory = $true)][long]$MesaId,
    [Parameter(Mandatory = $true)][long]$CommercialNumber,
    [Parameter(Mandatory = $true)][string]$From,
    [Parameter(Mandatory = $true)][string]$To,
    [Parameter(Mandatory = $true)][long]$StoreBRepeatedCommercialTicketId,
    [string]$LimaFrom,
    [string]$LimaTo,
    [long]$LimaExpectedTicketId
)

$ErrorActionPreference = "Stop"
$script:checks = 0

function Assert-True {
    param([bool]$Condition, [string]$Message)
    $script:checks++
    if (-not $Condition) {
        throw "FAIL [$script:checks] $Message"
    }
    Write-Host "PASS [$script:checks] $Message"
}

function Invoke-History {
    param(
        [string]$Token,
        [hashtable]$Query
    )
    $pairs = foreach ($entry in $Query.GetEnumerator()) {
        "$([uri]::EscapeDataString([string]$entry.Key))=$([uri]::EscapeDataString([string]$entry.Value))"
    }
    $uri = "$($BaseUrl.TrimEnd('/'))/api/tickets/paid?$($pairs -join '&')"
    Invoke-RestMethod -Method Get -Uri $uri -Headers @{ Authorization = "Bearer $Token" }
}

function Ticket-Ids {
    param($Page)
    @($Page.content | ForEach-Object { [long]$_.id })
}

function Assert-SingleTicket {
    param($Page, [long]$ExpectedId, [string]$Message)
    $ids = @(Ticket-Ids $Page)
    Assert-True ($Page.totalElements -eq 1 -and $ids.Count -eq 1 -and $ids[0] -eq $ExpectedId) $Message
}

if ([string]::IsNullOrWhiteSpace($StoreAToken) -or [string]::IsNullOrWhiteSpace($StoreBToken)) {
    throw "Faltan StoreAToken/StoreBToken o F9_HISTORY_STORE_A_TOKEN/F9_HISTORY_STORE_B_TOKEN."
}

$common = @{
    from = $From
    to = $To
    page = 0
    size = 100
}

try {
    $unfiltered = Invoke-History $StoreAToken $common
    $unfilteredIds = @(Ticket-Ids $unfiltered)
    Assert-True ($unfilteredIds -contains $CashTicketId) "Sin filtros conserva CASH"
    Assert-True ($unfilteredIds -contains $CardTicketId) "Sin filtros conserva CARD"
    Assert-True ($unfilteredIds -contains $MixedTicketId) "Sin filtros conserva MIXED"

    Assert-SingleTicket (Invoke-History $StoreAToken ($common + @{ paymentMethod = "CASH" })) `
        $CashTicketId "Filtro CASH"
    Assert-SingleTicket (Invoke-History $StoreAToken ($common + @{ paymentMethod = "CARD" })) `
        $CardTicketId "Filtro CARD"
    Assert-SingleTicket (Invoke-History $StoreAToken ($common + @{ paymentMethod = "MIXED" })) `
        $MixedTicketId "Filtro MIXED sin duplicar componentes"
    Assert-SingleTicket (Invoke-History $StoreAToken ($common + @{ userId = $PaidByUserId })) `
        $MixedTicketId "Filtro paidBy"
    Assert-SingleTicket (Invoke-History $StoreAToken ($common + @{ mesaId = $MesaId })) `
        $MixedTicketId "Filtro mesa histórica"
    Assert-SingleTicket (Invoke-History $StoreAToken ($common + @{ commercialNumber = $CommercialNumber })) `
        $MixedTicketId "Filtro número comercial Store A"

    $combined = $common + @{
        paymentMethod = "MIXED"
        userId = $PaidByUserId
        mesaId = $MesaId
        commercialNumber = $CommercialNumber
    }
    Assert-SingleTicket (Invoke-History $StoreAToken $combined) $MixedTicketId "Filtros combinados"

    $storeBNumber = Invoke-History $StoreBToken ($common + @{ commercialNumber = $CommercialNumber })
    Assert-SingleTicket $storeBNumber $StoreBRepeatedCommercialTicketId `
        "Mismo número comercial resuelve dentro de Store B"
    Assert-True (-not ((Ticket-Ids $storeBNumber) -contains $MixedTicketId)) `
        "Store B no recibe ticket homónimo de Store A"

    try {
        Invoke-History $StoreAToken ($common + @{ paymentMethod = "INVALID" }) | Out-Null
        throw "El paymentMethod inválido fue aceptado"
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Assert-True ($status -eq 400) "paymentMethod inválido devuelve HTTP 400"
    }

    try {
        Invoke-History $StoreAToken @{
            from = "2025-01-01"
            to = "2026-01-02"
            page = 0
            size = 10
        } | Out-Null
        throw "El rango de 367 días fue aceptado"
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Assert-True ($status -eq 422) "Rango superior a 366 días devuelve HTTP 422"
    }

    if ($LimaExpectedTicketId -gt 0 -and
            -not [string]::IsNullOrWhiteSpace($LimaFrom) -and
            -not [string]::IsNullOrWhiteSpace($LimaTo)) {
        $limaPage = Invoke-History $StoreBToken @{
            from = $LimaFrom
            to = $LimaTo
            page = 0
            size = 100
        }
        Assert-True ((Ticket-Ids $limaPage) -contains $LimaExpectedTicketId) `
            "El día comercial de la Store Lima contiene el ticket esperado"
    } else {
        Write-Host "SKIP caso Lima: no se proporcionó fixture temporal opcional."
    }

    Write-Host "Smoke historial/reportes completado: $script:checks checks."
} finally {
    # El script utiliza una fixture controlada preexistente y no crea filas.
    # Se eliminan referencias sensibles del proceso incluso cuando falla.
    $StoreAToken = $null
    $StoreBToken = $null
    Remove-Variable common, unfiltered, storeBNumber, combined, limaPage `
        -ErrorAction SilentlyContinue
}
