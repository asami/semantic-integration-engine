package org.simplemodeling.sie.context

import org.goldenport.cncf.component.Component
import org.simplemodeling.sie.service.RagService

final case class SieContext(
  ragService: RagService
) extends Component.ApplicationContext
