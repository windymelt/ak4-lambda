package com.github.windymelt.ak4lambda

import com.amazonaws.secretsmanager.caching.SecretCache
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.UpdateSecretRequest
import cats.effect.IO

object Secret:
  private val cache = new SecretCache()
  def currentToken(secretArn: String): IO[String] = IO:
    cache.getSecretString(secretArn)

  def updateCurrentToken(secretArn: String, token: String): IO[Unit] = IO:
    val req =
      new UpdateSecretRequest().withSecretId(secretArn).withSecretString(token)
    AWSSecretsManagerClientBuilder.defaultClient().updateSecret(req)
