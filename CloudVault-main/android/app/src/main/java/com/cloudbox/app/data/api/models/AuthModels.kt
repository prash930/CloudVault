package com.cloudbox.app.data.api.models

data class RegisterRequest(val email: String, val display_name: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class ForgotPasswordRequest(val email: String)
data class TokenResponse(val access_token: String, val token_type: String, val user: UserResponse)
data class MessageResponse(val message: String)
data class ErrorResponse(val detail: String)
