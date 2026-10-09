# School of Sorcery

Java REST service deployed as an OSGi module on Liferay DXP. It processes
applications using configurable council rules, returns accepted and rejected
applicants, and exposes the current state of the four houses.

## Requirements

- JDK 21
- Liferay DXP 2026.Q3.1
- Blade CLI for downloading and managing the local bundle
- A valid Liferay activation key
- An authenticated Liferay user

The Gradle wrapper is included. Bundle files and activation keys are not included.

## Build and run

From the workspace root on Windows PowerShell:

```powershell
.\gradlew.bat :modules:sorcery-rest:build
blade server init
```

Activate and configure the bundle following Liferay's instructions and the
workspace's GETTING_STARTED.md. Start the local server:

```powershell
blade server start
```

Once Liferay is available at http://localhost:8080, deploy the module:

```powershell
.\gradlew.bat :modules:sorcery-rest:deploy
```

## Prepare the request

The request contains an applications array and a councilRules object.
The following PowerShell commands support the supplied sample files,
including files wrapped in applications or councilRules properties:

```powershell
$appData = Get-Content .\sample-data\applications.json -Raw -Encoding UTF8 | ConvertFrom-Json
$ruleData = Get-Content .\sample-data\council-rules.json -Raw -Encoding UTF8 | ConvertFrom-Json

if ($appData.PSObject.Properties.Name -contains "applications") {
    $applications = @($appData.applications)
} else {
    $applications = @($appData)
}

if ($ruleData.PSObject.Properties.Name -contains "councilRules") {
    $rules = $ruleData.councilRules
} else {
    $rules = $ruleData
}

$payload = @{
    applications = @($applications)
    councilRules = $rules
} | ConvertTo-Json -Depth 100

[System.IO.File]::WriteAllText(
    (Join-Path $PWD "selection-request.json"),
    $payload,
    (New-Object System.Text.UTF8Encoding($false))
)
```

## REST endpoints

### POST /o/sorcery/selections

Processes a year using the applications and council rules in the request body.
A successful request replaces the current selection, allowing the same year
to be processed again with different rules.

```powershell
curl.exe -i --user "test@liferay.com" -H "Content-Type: application/json" --data-binary "@selection-request.json" http://localhost:8080/o/sorcery/selections
```

Replace the example username with your Liferay user's email. curl prompts for
the password.

The response includes:

- year, acceptedCount and rejectedCount
- accepted applicants with ranking position, admission score, invitation
  status, assigned house and house score
- rejected applicants with the rejection reason; applicants rejected for lack
  of places also include their ranking position and admission score
- house capacities, occupancy, available beds and applicant IDs

### GET /o/sorcery/houses

Returns the houses from the latest successful selection.

```powershell
curl.exe -i --user "test@liferay.com" http://localhost:8080/o/sorcery/houses
```

### Status codes

- 200: selection processed or house state returned
- 400: invalid input or inconsistent configuration
- 401: authentication failed
- 404: houses requested before any successful selection

Invalid requests preserve the previous successful selection.

## Selection rules

1. Apply vetoes in order: banned family, age, unacceptable weakness,
   application date. Report the first matching veto.
2. Invited applicants bypass vetoes and reserve a place.
3. Calculate admission scores from virtues, family, weaknesses and age.
   Unknown values contribute zero.
4. Rank by descending score, then ascending age, family name and first name.
   Applicant ID provides a deterministic final tie-break.
5. Admit invited applicants and fill remaining places from the ranking.
6. Assign houses in admission ranking order. Choose the highest-scoring
   house with a free bed; ties follow the council file's house order.

Invitations guarantee admission but do not increase ranking position or give
priority in house assignment. An invited applicant can therefore have a
ranking position beyond the number of available places.

Admission scores and house scores are calculated separately.

## Design

- SorceryModels: records representing input rules and selection results
- SelectionService: selection, ranking, validation and house assignment
- SorceryApplicationApplication: REST endpoints and JSON conversion

The service validates duplicate applicant IDs, house names and capacities,
date and age ranges, and overlapping age scoring ranges.

## State and limitations

State is stored in memory and shared between requests to the module.
Restarting or redeploying the module clears it.

POST requests are serialized. GET reads the latest published result.
State is published only after processing and response serialization succeed.

Matching of names and traits is case-sensitive. Invitations are matched using
the full name: firstName + a space + familyName.

If invited applicants exceed the available places, the request is rejected
with HTTP 400.

## Tests

```powershell
.\gradlew.bat :modules:sorcery-rest:test --tests "com.diego.sorcery.service.SelectionServiceTest"
```

Eight unit tests cover veto priority, invitations and ranking positions,
ranking tie-breaks, admission scoring, house scoring and capacity fallback,
zero-score house ties, inclusive date and age boundaries, and duplicate IDs.

Local REST checks also verified successful processing, house retrieval, and
preservation of the previous state after an invalid request.

With the supplied sample data, the verified result is 50 accepted and 150
rejected applicants. Occupancy: Lion 14, Serpent 12, Raven 12 and Badger 12.
