variable "teams_webhook_url_secret" {
  type        = string
  description = "The URL of the the MS Teams Webhook Secret"
  default     = ""
}
variable "project_id" {
  type        = string
  description = "The ID of the project to which resources will be applied."
}

#variable "secret_project_id" {
#  type        = string
#  description = "The ID of the secret project."
#  default     = ""
#}

variable "region" {
  type        = string
  description = "The region in which resources will be applied."
}

variable "flt_config" {
  type        = string
  description = "The filter configuration"
  default     = <<EOF
{;; :teams-endpoint "http://localhost:9876"
 :age 600000 #_31536000000 #_86400000 #_(* 1000 3600 24 365)
 :service {:include #{"Cloud Key Management Service"}}
 :status {:include #{"SERVICE_DISRUPTION" "SERVICE_OUTAGE"}}
 :locations {:include #{"europe-west1"}}}
EOF
}

variable "schedule" {
  type        = string
  description = "The schedule"
  default     = "*/10 * * * *"
}
