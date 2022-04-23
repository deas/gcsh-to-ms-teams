output "path" {
  description = "The path to the function build artifacts"
  value       = "${path.module}/dist/function"
}

output "entry_point" {
  description = "The function entry point"
  value       = "handle"
}

output "runtime" {
  description = "The runtime"
  value       = "nodejs14"
}