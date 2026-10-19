// Run only the isolated demo emulator with a minimal environment: CLI debug logs must not capture credentials.
const {spawnSync} = require('node:child_process');
const env = {FIREBASE_CLI_DISABLE_USAGE_STATS:'true',DEBUG:'',GCLOUD_PROJECT:'demo-clarity-income'};
const allowed = ['PATH','JAVA_HOME','SYSTEMROOT','WINDIR','COMSPEC','PATHEXT','TEMP','TMP','USERPROFILE','HOME',
  'HOMEDRIVE','HOMEPATH','APPDATA','LOCALAPPDATA','NODE_EXTRA_CA_CERTS','NODE_USE_SYSTEM_CA'];
for (const [key,value] of Object.entries(process.env)) if (allowed.includes(key.toUpperCase())) env[key]=value;
const result=spawnSync(process.execPath,[require.resolve('firebase-tools/lib/bin/firebase.js'),
  'emulators:exec','--config','../../firebase.income-test.json','--project','demo-clarity-income','--only','firestore',
  'node --test income.test.cjs'],{env,stdio:'inherit'});
if(result.error) console.error(result.error.message);
process.exitCode=result.status ?? 1;
