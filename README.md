# Google Cloud Service Health to MS Teams - GCP Cloud Function

Pull [Google Cloud Service Health](https://status.cloud.google.com), filter `most_recent_update` by `location`, `service_name`, `status` and `created` and send it to MS Teams.

For the time beeing, this function does not persist anything. It relies on `age` passed in with the filter. I am aware this might miss events in certain edge cases.

## Dev Requirements
- Java (for build, Runtime not quite yet)
- Node JS
- Terraform

## Usage (Target: NodeJS)
Install dependencies
```shell
yarn install || npm install
```

## Configuration
The function gets the configuration with the call. A [sample payload](samples/flt-config.edn) is provided.

If you want to deploy it on GCP, you might also want to provide the bare minimum of configuration via `terraform.tfvars`:

```terraform
project_id = "your-project-0815"
region     = "your-region"
```

### Deploy (terraform)
```shell
yarn run deploy || npm run deploy
```
### Development
REPL (with Calva):

Start your project with a REPL and connect -> `shadow-cljs` -> `:node` -> `:node`.

Or in a plain shell:
```shell
yarn run repl || npm run repl
```
Run dev process
```shell
yarn start || npm start
```
Now you should be able to evaluate forms from the REPL.

Test
```shell
yarn run test || npm run test
```

Build and run locally:
```shell
yarn run build || npm run build
yarn run serve || npm run serve
```
Send PubSub Payload (including CloudEvent headers) to local service
```shell
message=samples/flt-config.edn
endpoint=http://localhost:8080/a_topic_name

cat <<EOF | curl -d @- -X POST -H "Ce-Type: true" -H "Ce-Specversion: true"  -H "Ce-Source: true" -H "Ce-Id: true" -H "Content-Type: application/json" "${endpoint}" 
{
  "data": "$(base64 -w 0 $message)",
  "messageId": "3756413890745862",
  "publishTime": "2022-01-03T10:26:11.735Z"
}
EOF
```

Send PubSub Payload to .. cloud function/topic

```shell
PROJECT_ID=your-project-0815
function=$(gcloud --project ${PROJECT_ID} functions list | grep ^gcsh_teams | cut -d " " -f 1)
gcloud --project ${PROJECT_ID} functions call ${function} --data='{"message": "Hello World!"}'
gcloud --project ${PROJECT_ID} pubsub topics gcsh_issues --message '{ "fix": "me" }'
```

## TODO / Known Issues
- Implement proper release pipeline
- `gcp-build` does not work because there is no java on the builders
- build optimization is currently `simple` because `promesa/httpurr` appears to have an issue with `advanced` optimization
- Simplify deployment : One call on fresh source should take care of everything
- Build better tests
- Implement hot reloading for `serve`
- Finish cljc, do JVM host
- `npm start` appears to default to ipv6 with recent node. That does not appear to play with ootb Calva REPL
- We may want to switch over to [`google-event-function`](https://github.com/terraform-google-modules/terraform-google-event-function) because the scheduled version does not support secrets atm.
