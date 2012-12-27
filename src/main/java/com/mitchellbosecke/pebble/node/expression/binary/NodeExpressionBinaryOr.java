package com.mitchellbosecke.pebble.node.expression.binary;

import com.mitchellbosecke.pebble.compiler.Compiler;
import com.mitchellbosecke.pebble.node.Node;
import com.mitchellbosecke.pebble.node.expression.NodeExpressionBinary;

public class NodeExpressionBinaryOr extends NodeExpressionBinary {

	public NodeExpressionBinaryOr() {
		super();
	}

	public NodeExpressionBinaryOr(int lineNumber, Node left, Node right) {
		super(lineNumber, left, right);
	}

	@Override
	public void operator(Compiler compiler) {
		compiler.raw("||");
	}
}