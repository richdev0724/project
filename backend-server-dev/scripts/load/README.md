# Flow Filter Load Tests

## 50-user post-deploy verification

Run this after `Deploy Backend` succeeds to verify authenticated Flow filtering under concurrent load:

```powershell
$env:MARKETMONO_ACCESS_TOKEN = '<access token>'
.\scripts\load\flow-filter-50-user-load.ps1 -BaseUrl https://marketmono.com -Users 50 -IterationsPerUser 5 -RampSeconds 10 -EndpointMode Mixed
```

The script can also log in with `MARKETMONO_EMAIL` and `MARKETMONO_PASSWORD`, but committed scripts and docs must not contain raw test-account credentials. If login returns `requiresTwoFactor=true`, provide `MARKETMONO_ACCESS_TOKEN` instead.

Default thresholds:

| Threshold     | Default |
|---------------|---------|
| Failure rate  | `<= 5%` |
| Busy rate     | `<= 20%` |
| P95 latency   | `<= 8000ms` |

Use `-OutputPath` to save the JSON summary into a local report path when evidence needs to be retained.
