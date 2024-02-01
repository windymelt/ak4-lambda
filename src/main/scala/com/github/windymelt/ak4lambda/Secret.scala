package com.github.windymelt.ak4lambda

import com.amazonaws.secretsmanager.caching.SecretCache
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.UpdateSecretRequest

object Secret:
  private val cache = new SecretCache()
  def currentToken(secretId: String): String = cache.getSecretString(secretId)
  def updateCurrentToken(secretId: String, token: String): Unit =
    val req =
      new UpdateSecretRequest().withSecretId(secretId).withSecretString(token)
    AWSSecretsManagerClientBuilder.defaultClient().updateSecret(req)
