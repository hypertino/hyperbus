/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model.annotations

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

private[annotations] trait AnnotationMacroImplBase {
  val c: Context

  import c.universe._

  def run(annottees: Seq[c.Expr[Any]]): c.Expr[Any] = {
    annottees.map(_.tree) match {
      case (clz: ClassDef) :: Nil => updateClass(clz)
      case (clz: ClassDef) :: (clzCompanion: ModuleDef) :: Nil => updateClass(clz, Some(clzCompanion))
      case _ => invalidAnnottee()
    }
  }

  protected def updateClass(existingClass: ClassDef, existingCompanion: Option[ModuleDef] = None): c.Expr[Any]

  protected def invalidAnnottee() = c.abort(c.enclosingPosition, "This annotation can only be used on class")

  protected def fresh(prefix: String): TermName = TermName(c.freshName(prefix))
}
