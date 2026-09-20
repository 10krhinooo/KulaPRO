package com.example.kulapro.util

/**
 * A message written for the person reading it.
 *
 * Repositories throw this when the reason for a failure is already something a diner can act
 * on. Anything else that goes wrong is an internal detail and gets translated by
 * [userMessageFor] instead, so a stack trace or a backend error code never reaches a screen.
 */
class UserFacingException(message: String) : Exception(message)
