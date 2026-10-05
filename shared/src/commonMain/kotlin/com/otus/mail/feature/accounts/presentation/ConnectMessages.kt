package com.otus.mail.feature.accounts.presentation

import com.otus.mail.feature.accounts.domain.ConnectFailure

fun ConnectFailure.message(): String =
    when (this) {
        ConnectFailure.WrongCredentials -> {
            "The server rejected this username or password. " +
                "If your account uses two-factor sign-in, use an app password."
        }

        ConnectFailure.NotPermitted -> {
            "The server accepted the sign-in but doesn't allow this account to use JMAP mail."
        }

        ConnectFailure.InvalidUsername -> {
            "Usernames can't contain “:”."
        }

        ConnectFailure.NotJmap -> {
            "No JMAP service was found at this address. Check it, or enter the full JMAP session URL."
        }

        ConnectFailure.NoMail -> {
            "This account doesn't offer mail over JMAP."
        }

        ConnectFailure.Unreachable -> {
            "Couldn't reach the server. Check the address and your connection. " +
                "Its certificate must be trusted by this device."
        }

        ConnectFailure.Insecure -> {
            "The server tried to use an insecure connection, so it was stopped."
        }

        ConnectFailure.ServerError -> {
            "The server returned an error. Try again later."
        }

        ConnectFailure.StorageFailed -> {
            "The account couldn't be saved on this device. Try again."
        }
    }

fun confirmHostMessage(host: String): String =
    "This server hands sign-in to $host. Your password will be sent there. Continue only if you trust it."
