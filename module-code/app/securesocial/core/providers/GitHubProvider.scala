/**
 * Copyright 2012 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package securesocial.core.providers

import securesocial.core._
import play.api.{Logger, Application}
import play.api.libs.ws.WS
import securesocial.core.IdentityId
import securesocial.core.SocialUser
import play.api.libs.ws.Response
import securesocial.core.AuthenticationException
import scala.Some

/**
 * A GitHub provider
 *
 */
class GitHubProvider(application: Application) extends OAuth2Provider(application) {
  val GetAuthenticatedUser = "https://api.github.com/user?access_token=%s"
  val AccessToken = "access_token"
  val TokenType = "token_type"
  val Message = "message"
  val Id = "id"
  val Name = "name"
  val AvatarUrl = "avatar_url"
  val Email = "email"

  override def id = GitHubProvider.GitHub

  override protected def buildInfo(response: Response): OAuth2Info = {
    val parsed: Array[Tuple2[String, String]] = for {
      nameValueString <- response.body.split("&")
      nameValueArray = nameValueString.split("=")
      if (nameValueArray.size == 2)
    } yield (nameValueArray(0), nameValueArray(1))
    val map = parsed.toList.toMap

    val tokenOption = map.get(AccessToken)
    val tokenTypeOption = map.get(TokenType)
    (tokenOption, tokenTypeOption) match {
      case (Some(token: String), Some(_)) => OAuth2Info(token, tokenTypeOption, None)
      case _ =>
        Logger.error("[securesocial] invalid response format for accessToken")
        throw new AuthenticationException()
    }
  }

  /**
   * Subclasses need to implement this method to populate the User object with profile
   * information from the service provider.
   *
   * @param user The user object to be populated
   * @return A copy of the user object with the new values set
   */
  def fillProfile(user: SocialUser): SocialUser = {
    val promise = WS.url(GetAuthenticatedUser.format(user.oAuth2Info.get.accessToken)).get()
    try {
      val response = awaitResult(promise)
      val me = response.json
      (me \ Message).asOpt[String] match {
        case Some(msg) => {
          Logger.error("[securesocial] error retrieving profile information from GitHub. Message = %s".format(msg))
          throw new AuthenticationException()
        }
        case _ => {
          val userId = (me \ Id).as[Int]
          val displayName = (me \ Name).asOpt[String].getOrElse("")
          val avatarUrl = (me \ AvatarUrl).asOpt[String]
          val email = (me \ Email).asOpt[String].filter( !_.isEmpty )
          user.copy(
            identityId = IdentityId(userId.toString, id),
            fullName = displayName,
            avatarUrl = avatarUrl,
            email = email
          )
        }
      }
    } catch {
      case e: Exception => {
        Logger.error( "[securesocial] error retrieving profile information from github", e)
        throw new AuthenticationException()
      }
    }
  }
}

object GitHubProvider {
  val GitHub = "github"
}
