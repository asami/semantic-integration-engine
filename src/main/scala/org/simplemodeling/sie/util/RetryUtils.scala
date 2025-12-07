package org.simplemodeling.sie.util

import cats.effect.IO
import scala.concurrent.duration.*

/*
 * @since   Dec.  7, 2025
 * @version Dec.  7, 2025
 * @author  ASAMI, Tomoharu
 */
object RetryUtil:
  def retryIO[A](
      attempts: Int,
      delay: FiniteDuration,
      label: String = ""
  )(io: IO[A]): IO[A] =
    io.handleErrorWith { e =>
      if attempts > 1 then
        IO.println(s"[RetryUtil] $label failed, retrying... attempts left: ${attempts - 1}, error: ${e.getMessage}") *>
          IO.sleep(delay) *>
          retryIO(attempts - 1, delay, label)(io)
      else
        IO.raiseError(e)
    }
