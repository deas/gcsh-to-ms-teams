resource "random_pet" "main" {
  length    = 2
  separator = "-"
}

# TODO: No java on gcp js build -> so we cannot use shadown-cljs there
resource "local_file" "package" {
  filename = "${path.module}/dist/function/package.json"
  content  = <<EOF
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

/*
resource "local_file" "shadow" {
  content  = replace(file("${path.module}/shadow-cljs.edn"), "/(src\\/)|(dist/function\\/)/", "")
  filename = "${path.module}/src/shadow-cljs.edn"
}
*/

module "gcsh_to_teams" {
  source                          = "terraform-google-modules/scheduled-function/google"
  version                         = "2.3.0"
  # In case we want to introduce the secrets here
  # source                          = "git::https://github.com/deas/terraform-google-scheduled-function?ref=v2.3.0"
  project_id                      = var.project_id
  job_name                        = "gcsh_issues_to_teams"
  job_description                 = "Scheduled Google Cloud Service Health Issues for MS Teams"
  job_schedule                    = var.schedule
  function_entry_point            = "handle"
  function_source_directory       = "${path.module}/dist/function"
  function_source_dependent_files = [local_file.package] #, local_file.shadow]
  function_name                   = "gcsh_teams"
  function_description            = "Send Filtered Google Cloud Service Health Issues to MS Teams"
  region                          = var.region
  topic_name                      = "gcsh_issues"
  function_runtime                = "nodejs14"
  function_available_memory_mb    = "128"
  message_data                    = base64encode(var.flt_config)
  #function_environment_variables = {
  #  FOO        = var.foo
  #}
}

/*

resource "null_resource" "wait_for_function" {
  provisioner "local-exec" {
    command = "sleep 60"
  }

  depends_on = [module.gcsh_to_teams]
}
*/
#resource "null_resource" "npm_build" {
#  provisioner "local-exec" {
#    command = "cd ${path.module}/src && npm install && npm run build"
#  }
#
#  triggers = {
#    index   = sha256(file("${path.module}/src/index.js"))
#    package = sha256(file("${path.module}/src/package.json"))
#    lock    = sha256(file("${path.module}/src/package-lock.json"))
#    node    = sha256(join("", fileset(path.module, "src/**/*.js")))
#  }
#}
