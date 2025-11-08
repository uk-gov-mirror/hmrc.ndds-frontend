/*
 * Copyright 2025 HM Revenue & Customs
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
 */

package forms

import forms.mappings.Mappings
import models.SuspensionPeriodRange
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.Messages
import utils.DateFormats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class SuspensionPeriodRangeDateFormProvider @Inject() extends Mappings {

  private val MaxMonthsAhead = 6
  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

  def apply(
    planStartDateOpt: Option[LocalDate],
    planEndDateOpt: Option[LocalDate],
    earliestStartDate: LocalDate
  )(implicit messages: Messages): Form[SuspensionPeriodRange] = {

    Form(
      mapping(
        "suspensionPeriodRangeStartDate" -> customPaymentDate(
          invalidKey     = "suspensionPeriodRangeDate.error.invalid.startDate.base",
          allRequiredKey = "suspensionPeriodRangeStartDate.error.required.all",
          twoRequiredKey = "suspensionPeriodRangeStartDate.error.required.two",
          requiredKey    = "suspensionPeriodRangeStartDate.error.required",
          dateFormats    = DateFormats.defaultDateFormats
        ).verifying(startDateConstraint(planStartDateOpt, planEndDateOpt, earliestStartDate)),
        "suspensionPeriodRangeEndDate" -> customPaymentDate(
          invalidKey     = "suspensionPeriodRangeDate.error.invalid.endDate.base",
          allRequiredKey = "suspensionPeriodRangeEndDate.error.required.all",
          twoRequiredKey = "suspensionPeriodRangeEndDate.error.required.two",
          requiredKey    = "suspensionPeriodRangeEndDate.error.required",
          dateFormats    = DateFormats.defaultDateFormats
        ).verifying(endDateConstraint(planStartDateOpt, planEndDateOpt, earliestStartDate))
      )(SuspensionPeriodRange.apply)(range => Some((range.startDate, range.endDate)))
    )
  }

  private def startDateBounds(
    planStartDateOpt: Option[LocalDate],
    earliestStartDate: LocalDate,
    planEndDateOpt: Option[LocalDate]
  ): (LocalDate, LocalDate) = {
    val lower = planStartDateOpt.fold(earliestStartDate)(psd => if (psd.isAfter(earliestStartDate)) psd else earliestStartDate)
    val upper = planEndDateOpt.fold(LocalDate.now().plusMonths(MaxMonthsAhead)) { ped =>
      val sixMonthsFromToday = LocalDate.now().plusMonths(MaxMonthsAhead)
      if (ped.isBefore(sixMonthsFromToday)) ped else sixMonthsFromToday
    }
    (lower, upper)
  }

  private def endDateBounds(startDate: LocalDate, planEndDateOpt: Option[LocalDate]): (LocalDate, LocalDate) = {
    val lower = startDate
    val upper = planEndDateOpt.fold(startDate.plusMonths(MaxMonthsAhead)) { ped =>
      val sixMonthsFromStart = startDate.plusMonths(MaxMonthsAhead)
      if (ped.isBefore(sixMonthsFromStart)) ped else sixMonthsFromStart
    }
    (lower, upper)
  }

  private def startDateConstraint(
    planStartDateOpt: Option[LocalDate],
    planEndDateOpt: Option[LocalDate],
    earliestStartDate: LocalDate
  )(implicit messages: Messages): Constraint[LocalDate] =
    Constraint("suspensionPeriodRangeStartDate") { startDate =>
      val (lower, upper) = startDateBounds(planStartDateOpt, earliestStartDate, planEndDateOpt)
      if (!startDate.isBefore(lower) && !startDate.isAfter(upper)) Valid
      else
        Invalid(
          ValidationError(
            messages("suspensionPeriodRangeDate.error.startDate", lower.format(dateFormatter), upper.format(dateFormatter))
          )
        )
    }

  private def endDateConstraint(
    planStartDateOpt: Option[LocalDate],
    planEndDateOpt: Option[LocalDate],
    earliestStartDate: LocalDate
  )(implicit messages: Messages): Constraint[LocalDate] =
    Constraint("suspensionPeriodRangeEndDate") { endDate =>
      val startDate = planStartDateOpt.getOrElse(earliestStartDate)
      val (lower, upper) = endDateBounds(startDate, planEndDateOpt)

      if (!endDate.isBefore(lower) && !endDate.isAfter(upper)) Valid
      else
        Invalid(
          ValidationError(
            messages("suspensionPeriodRangeDate.error.endDate", lower.format(dateFormatter), upper.format(dateFormatter))
          )
        )
    }
}
