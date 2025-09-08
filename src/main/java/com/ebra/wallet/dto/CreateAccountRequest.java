package com.ebra.wallet.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateAccountRequest {
    @NotBlank(message = "Username is required")
    private String username;

    public CreateAccountRequest() {}

    public CreateAccountRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}