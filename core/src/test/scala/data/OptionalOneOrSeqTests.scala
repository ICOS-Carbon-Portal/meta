package se.lu.nateko.cp.meta.core.tests.data

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.*

class OptionalOneOrSeqTests extends AnyFunSpec{

	// (input seq, expected OptionalOneOrSeq, expected Option[Seq])
	private val cases: Seq[(IndexedSeq[String], OptionalOneOrSeq[String], Option[Seq[String]])] = Seq(
		(IndexedSeq(), None, None),
		(IndexedSeq("a"), Some(Left("a")), Some(Seq("a"))),
		(IndexedSeq("a", "b"), Some(Right(Seq("a", "b"))), Some(Seq("a", "b")))
	)

	describe("OptionalOneOrSeq.fromSeq"){
		it("gives None for empty, a single value for one item, and a list for several"){
			for ((input, expected, _) <- cases) withClue(s"input=$input: "){
				assert(OptionalOneOrSeq.fromSeq(input) == expected)
			}
		}
	}

	describe("flattenToSeq"){
		it("returns all items in order, or an empty Seq for None"){
			for ((input, value, _) <- cases) withClue(s"value=$value: "){
				assert(value.flattenToSeq == input)
			}
		}
		it("is the inverse of fromSeq"){
			for ((input, _, _) <- cases)
				assert(OptionalOneOrSeq.fromSeq(input).flattenToSeq == input)
		}
		it("works on a non-optional OneOrSeq"){
			val one: OneOrSeq[String] = Left("a")
			val many: OneOrSeq[String] = Right(Seq("a", "b"))
			assert(one.flattenToSeq == Seq("a"))
			assert(many.flattenToSeq == Seq("a", "b"))
		}
	}

	describe("flattenToOptionSeq"){
		it("keeps None as None and turns one or several items into Some(Seq)"){
			for ((_, value, expected) <- cases) withClue(s"value=$value: "){
				assert(value.flattenToOptionSeq == expected)
			}
		}
	}

	describe("mapO3"){
		it("maps every item while keeping the None / single / list shape"){
			assert((None: OptionalOneOrSeq[String]).mapO3(_.toUpperCase) == None)
			assert((Some(Left("a")): OptionalOneOrSeq[String]).mapO3(_.toUpperCase) == Some(Left("A")))
			assert((Some(Right(Seq("a", "b"))): OptionalOneOrSeq[String]).mapO3(_.toUpperCase) == Some(Right(Seq("A", "B"))))
		}
	}

	describe("non-normalized values (allowed by the type, never produced by fromSeq)"){
		it("still flatten correctly"){
			val emptyList: OptionalOneOrSeq[String] = Some(Right(Seq()))
			val singletonList: OptionalOneOrSeq[String] = Some(Right(Seq("a")))
			assert(emptyList.flattenToSeq == Seq())
			assert(emptyList.flattenToOptionSeq == Some(Seq()))
			assert(singletonList.flattenToSeq == Seq("a"))
		}
	}
}
