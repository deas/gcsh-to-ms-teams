
resource "random_pet" "main" {
  length    = 2
  separator = "-"
}

module "gcsh_to_teams" {
  # In case we want to introduce the secrets here
  # source                       = "terraform-google-modules/scheduled-function/google"
  # version                      = "2.3.0"
  source                       = "git::https://github.com/deas/terraform-google-scheduled-function?ref=catchup-passthrough"
  project_id                   = var.project_id
  job_name                     = "gcsh_issues_to_teams_${random_pet.main.id}"
  job_description              = "Scheduled Google Cloud Service Health Issues for MS Teams"
  job_schedule                 = var.schedule
  function_entry_point         = module.function.entry_point
  function_source_directory    = module.function.path
    # function_source_dependent_files = [local_file.package] #, local_file.shadow]
  function_name                = "gcsh_teams_${random_pet.main.id}"
  function_description         = "Send Filtered Google Cloud Service Health Issues to MS Teams"
  region                       = var.region
  topic_name                   = "gcsh_issues_${random_pet.main.id}"
  function_runtime             = module.function.runtime
  function_available_memory_mb = "128"
  #function_secret_environment_variables = [
  #  {
  #    key         = "TEAMS_WEBHOOK_URL"
  #    project_id  = var.secret_project_id
  #    secret_name = var.teams_webhook_url_secret
  #    version     = "1"
  #  }
  #]
  function_environment_variables = {
    TEAMS_WEBHOOK_URL = var.teams_webhook_url_secret
  }
  message_data = base64encode(var.flt_config)

}

# A bit hacky, ...
module "function" {
  source = "../.."
}

# ... however: Pretty much all solutions - http provider, null provider, ... have issues
# https://github.com/hashicorp/terraform-provider-local/issues/28
#
/*
data "http" "function" {
  url = module.function.function_url
}

resource "local_file" "function" {
  filename = "${path.module}/dist/index.js"
  content  = data.http.function.body
}

# Lifecyle issues
resource "null_resource" "function_sources" {
  provisioner "local-exec" {
    command = <<EOF
mkdir -p dist && curl -s -f -o dist/index.js ${module.function.function_url}
EOF
  }
  #triggers = {
  #  index   = sha256(file("${path.module}/dist/index.js"))
  #}
  # depends_on = [module.gcsh_to_teams]
}
*/

# TODO: No java on gcp js build -> so we cannot use shadown-cljs there
/*
resource "local_file" "package" {
  filename = "${path.module}/dist/package.json"
  content  = module.function.content_package <<EOF
{
  "name": "cljc-gcsh-to-teams-fn",
  "version": "0.0.1",
  "private": true,
  "dependencies": {
    "strftime": "^0.10.0"
  }
}
EOF
}
*/


/*
resource "local_file" "shadow" {
  content  = replace(file("${path.module}/shadow-cljs.edn"), "/(src\\/)|(dist/function\\/)/", "")
  filename = "${path.module}/src/shadow-cljs.edn"
}
*/

