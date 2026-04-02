# Path-Based Filtering: Quick Reference

## Pipeline Flow Chart

```
┌─────────────────────────────────────────────────────────────┐
│                    Developer Push/PR                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
         ┌───────────────────────────┐
         │  JOB 0: Detect Changes    │
         │  (Compare with main)      │
         └────────┬──────────────────┘
                  │
      ┌───────────┴───────────┐
      │                       │
      ▼                       ▼
  any-changed?          Which services?
  true/false            crew, roster, etc.
      │                       │
      └───────────┬───────────┘
                  │
           ┌──────▼──────┐
           │   If FALSE  │
           │   Pipeline  │ ◀── SKIP ALL (no changes)
           │    Exits    │
           └─────────────┘
                  │
           ┌──────▼──────────────────────────────────────────┐
           │           If TRUE (Changes Detected)             │
           └───────────┬──────────────────────────────────────┘
                       │
                       ▼
         ┌─────────────────────────────┐
         │   JOB 1: Code Quality       │
         │   (runs if any-changed)     │
         └────────┬────────────────────┘
                  │
                  ▼
    ┌────────────────────────────┐
    │  JOB 2: Test (Matrix)      │
    │  ┌──────────────────────┐  │
    │  │ RUNS ONLY IF SERVICE │  │
    │  │ HAS CHANGES:         │  │
    │  │ ✅ crew-service      │  │
    │  │ ❌ auth-service      │  │
    │  │ ❌ api-gateway       │  │
    │  │ ...                  │  │
    │  └──────────────────────┘  │
    └────────┬────────────────────┘
             │
             ▼
    ┌────────────────────────────┐
    │   JOB 3: Security Scan     │
    │ (runs if any-changed)      │
    └────────┬────────────────────┘
             │
             ▼
    ┌────────────────────────────┐
    │  JOB 4: Build & Push       │
    │  (only changed services)   │
    │  ┌──────────────────────┐  │
    │  │ SKIPS SERVICES WITH  │  │
    │  │ NO CHANGES           │  │
    │  └──────────────────────┘  │
    └────────┬────────────────────┘
             │
       (Push to main branch only)
             │
             ▼
    ┌────────────────────────────┐
    │  JOB 5: Deploy Staging     │
    │ (only changed services)    │
    └────────┬────────────────────┘
             │
             ▼
    ┌────────────────────────────┐
    │  JOB 6: Deploy Production  │
    │  (Manual Approval Gate)    │
    │ (only changed services)    │
    └────────────────────────────┘
```

---

## Decision Matrix: What Gets Built?

| What Changed | Detect Output | What Runs |
|---|---|---|
| `crew-service/src/**` | crew=true, others=false | ✅ crew tests/build/deploy only |
| `auth-service/pom.xml` | auth=true, others=false | ✅ auth tests/build/deploy only |
| `pom.xml` (root) | all=true | ✅ ALL 6 services |
| `.github/workflows/ci-cd.yml` | all=true | ✅ ALL 6 services |
| `docker-compose.yml` | all=true | ✅ ALL 6 services |
| `README.md` | any-changed=false | ⏭️ SKIP ENTIRE PIPELINE |
| No changes (rebase) | any-changed=false | ⏭️ SKIP ENTIRE PIPELINE |

---

## Performance Impact

### Before Path-Based Filtering
```
Single Service Change (crew-service)
  code-quality:           3 min
  test (all 6 services):  18 min  ← WASTE: 15 min on unrelated services
  security-scan:          2 min
  build (all 6):          6 min   ← WASTE: 5 min building unrelated images
  deploy-staging (all 6): 3 min   ← WASTE: Redeploying untouched services
  deploy-prod (all 6):    3 min   ← WASTE: Redeploying untouched services
  ────────────────────────────────
  TOTAL:                  35 min ❌ WASTEFUL
```

### After Path-Based Filtering
```
Single Service Change (crew-service)
  code-quality:           3 min
  test (crew only):       3 min   ← OPTIMIZED: Only 1 service
  security-scan:          2 min
  build (crew only):      1 min   ← OPTIMIZED: Only 1 image
  deploy-staging (crew):  1 min   ← OPTIMIZED: Only 1 service
  deploy-prod (crew):     1 min   ← OPTIMIZED: Only 1 service
  ────────────────────────────────
  TOTAL:                  11 min ✅ 3× FASTER
```

### Full Build (All Files Changed)
```
Root File Change (pom.xml)
  code-quality:           3 min
  test (all 6):           18 min  ✅ NECESSARY: Core dependencies updated
  security-scan:          2 min
  build (all 6):          6 min   ✅ NECESSARY
  deploy-staging (all 6): 3 min   ✅ NECESSARY
  deploy-prod (all 6):    3 min   ✅ NECESSARY
  ────────────────────────────────
  TOTAL:                  35 min ✅ APPROPRIATE FOR SCOPE
```

---

## How to Read Job Logs

### Example: Only crew-service Changed

**Step 1: View Detect Changes Output**
```
=== Changed files ===
crew-service/pom.xml
crew-service/src/main/java/CriewService.java

=== Detection Results ===
api-gateway=false
auth-service=false
crew-service=true
flight-service=false
roster-service=false
notification-service=false
any-changed=true
```

**Step 2: Observe Skipped Jobs**
```
Job: test[api-gateway]        ⏭️  SKIPPED (condition not met)
Job: test[auth-service]       ⏭️  SKIPPED (condition not met)
Job: test[crew-service]       ✅ QUEUED
Job: test[flight-service]     ⏭️  SKIPPED (condition not met)
Job: test[roster-service]     ⏭️  SKIPPED (condition not met)
Job: test[notification]       ⏭️  SKIPPED (condition not met)
```

**Step 3: View Deployment Output**
```
🔄 Deploying crew-service to staging...
⏭️  Skipping auth-service (no changes)
⏭️  Skipping api-gateway (no changes)
⏭️  Skipping flight-service (no changes)
⏭️  Skipping roster-service (no changes)
⏭️  Skipping notification-service (no changes)
✅ crew-service deployed successfully
```

---

## Key Code Snippets

### Detecting Changes
```bash
# For PRs: Compare with base branch
git diff --name-only origin/${{ github.base_ref }}...HEAD

# For pushes: Compare with previous commit
git diff --name-only HEAD^ HEAD

# Check if service changed
if [[ "$file" =~ ^crew-service/ ]]; then
  services[crew-service]=true
fi
```

### Skipping Jobs Per Service
```yaml
if: needs.detect-changes.outputs[matrix.service] == 'true'
```

### Conditional Deployment
```bash
for svc in api-gateway auth-service crew-service ...; do
  if [ "${{ needs.detect-changes.outputs[svc] }}" == "true" ]; then
    kubectl set image deployment/$svc ...
  else
    echo "⏭️  Skipping $svc (no changes)"
  fi
done
```

---

## Deployment Scenarios

### Scenario A: Team A Deploys crew-service
```
Approver clicks "Approve" in GitHub UI
↓
Deploy Production Job Runs
↓
Loops through all 6 services
↓
Only crew-service gets updated:
  kubectl set image deployment/crew-service ...
↓
Other services get:
  ⏭️  Skipping auth-service (no changes)
  ⏭️  Skipping api-gateway (no changes)
  ...
↓
✅ Result: Only crew-service deployed
```

### Scenario B: Team B Deploys while Team A is Deploying
```
Both crews are in the approval queue
↓
Approver approves Team B's PR (roster-service)
↓
Deploy Production Job for roster-service runs
↓
Only roster-service updated (crew-service untouched)
↓
Different services deployed independently
↓
✅ Result: No conflicts, parallel deployment
```

---

## Troubleshooting Commands

### Check What Files Changed in Your PR
```bash
# In GitHub UI: PR → Files Changed tab shows all modified files

# Via CLI:
git diff main..your-branch --name-only
```

### Force Full Build (Test)
```bash
# Edit .github/workflows/ci-cd.yml
git add .github/workflows/ci-cd.yml
git push

# OR edit root pom.xml
git add pom.xml
git push

# This triggers all-services build because root files changed
```

### Debug Detection Logic
```
Look at workflow logs:
GitHub Actions → CI/CD Pipeline → detect-changes job
↓
Check GITHUB_OUTPUT section
↓
Verify which services show "true"
```

