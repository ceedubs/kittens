/*
 * Copyright (c) 2015 Miles Sabin
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

package cats.derived

import cats._, free.Trampoline, Trampoline.done, std.function._
import shapeless._

object functor {
  implicit def apply[F[_]](implicit mff: WrappedOrphan[MkFunctor[F]]): Functor[F] = mff.instance
}

trait MkFunctor[F[_]] extends Functor[F] {
  def map[A, B](fa: F[A])(f: A => B): F[B] = trampolinedMap(fa){ a => done(f(a)) }.run

  def trampolinedMap[A, B](fa: F[A])(f: A => Trampoline[B]): Trampoline[F[B]]
}

object MkFunctor extends MkFunctor0 {
  def apply[F[_]](implicit mff: MkFunctor[F]): MkFunctor[F] = mff

  implicit def functor[F[_]](implicit ff: Functor[F]): MkFunctor[F] =
    new MkFunctor[F] {
      override def map[A, B](fa: F[A])(f: A => B): F[B] = ff.map(fa)(f)

      def trampolinedMap[A, B](fa: F[A])(f: A => Trampoline[B]): Trampoline[F[B]] =
        done(map(fa){ a => f(a).run })
    }
}

trait MkFunctor0 extends MkFunctor1 {
  // Induction step for products
  implicit def hcons[F[_]](implicit ihc: IsHCons1[F, MkFunctor, MkFunctor]): MkFunctor[F] =
    new MkFunctor[F] {
      def trampolinedMap[A, B](fa: F[A])(f: A => Trampoline[B]): Trampoline[F[B]] = {
        import ihc._
        val (hd, tl) = unpack(fa)
        for {
          fhd <- fh.trampolinedMap(hd)(f)
          ftl <- ft.trampolinedMap(tl)(f)
        } yield pack(fhd, ftl)
      }
    }

  // Induction step for coproducts
  implicit def ccons[F[_]](implicit icc: IsCCons1[F, MkFunctor, MkFunctor]): MkFunctor[F] =
    new MkFunctor[F] {
      def trampolinedMap[A, B](fa: F[A])(f: A => Trampoline[B]): Trampoline[F[B]] = {
        import icc._
        unpack(fa) match {
          case Left(hd)  => fh.trampolinedMap(hd)(f).map { fhd => pack(Left(fhd)) }
          case Right(tl) => ft.trampolinedMap(tl)(f).map { ftl => pack(Right(ftl)) }
        }
      }
    }
}

trait MkFunctor1 extends MkFunctor2 {
  implicit def split[F[_]](implicit split: Split1[F, MkFunctor, MkFunctor]): MkFunctor[F] =
    new MkFunctor[F] {
      def trampolinedMap[A, B](fa: F[A])(f: A => Trampoline[B]): Trampoline[F[B]] = {
        import split._
        fo.trampolinedMap(unpack(fa))(fi.trampolinedMap(_)(f)).map(pack)
      }
    }
}

trait MkFunctor2 extends MkFunctor3 {
  implicit def generic[F[_]](implicit gen: Generic1[F, MkFunctor]): MkFunctor[F] =
    new MkFunctor[F] {
      def trampolinedMap[A, B](fa: F[A])(f: A => Trampoline[B]): Trampoline[F[B]] =
        gen.fr.trampolinedMap(gen.to(fa))(f).map(gen.from)
    }
}

trait MkFunctor3 {
  implicit def constFunctor[T]: MkFunctor[Const[T]#λ] =
    new MkFunctor[Const[T]#λ] {
      def trampolinedMap[A, B](t: T)(f: A => Trampoline[B]): Trampoline[T] = done(t)
    }
}
