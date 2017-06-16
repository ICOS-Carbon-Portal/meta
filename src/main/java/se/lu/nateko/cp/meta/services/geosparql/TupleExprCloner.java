package se.lu.nateko.cp.meta.services.geosparql;

import org.eclipse.rdf4j.query.algebra.TupleExpr;

/**
 * Helper class to be able to clone TupleExpr from Scala.
 * Does not work directly due to non-overriding of Object.clone() method in TupleExpr
 */
public class TupleExprCloner {

	public static TupleExpr cloneExpr(TupleExpr expr){
		return expr.clone();
	}
}
