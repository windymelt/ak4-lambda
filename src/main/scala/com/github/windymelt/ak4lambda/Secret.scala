package com.github.windymelt.ak4lambda

import com.amazonaws.secretsmanager.caching.SecretCache
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.UpdateSecretRequest

object Secret:
  private val SECRET_ID = ""
  private val cache = new SecretCache()
  def getCurrentToken(): String = cache.getSecretString(SECRET_ID)
  def updateCurrentToken(token: String): Unit =
    val req =
      new UpdateSecretRequest().withSecretId(SECRET_ID).withSecretString(token)
    AWSSecretsManagerClientBuilder.defaultClient().updateSecret(req)
