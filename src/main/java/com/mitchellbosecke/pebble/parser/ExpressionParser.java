package com.mitchellbosecke.pebble.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.mitchellbosecke.pebble.error.SyntaxException;
import com.mitchellbosecke.pebble.lexer.Token;
import com.mitchellbosecke.pebble.lexer.TokenStream;
import com.mitchellbosecke.pebble.node.NodeExpression;
import com.mitchellbosecke.pebble.node.expression.NodeExpressionArguments;
import com.mitchellbosecke.pebble.node.expression.NodeExpressionBinary;
import com.mitchellbosecke.pebble.node.expression.NodeExpressionConstant;
import com.mitchellbosecke.pebble.node.expression.NodeExpressionDeclaration;
import com.mitchellbosecke.pebble.node.expression.NodeExpressionGetAttribute;
import com.mitchellbosecke.pebble.node.expression.NodeExpressionName;
import com.mitchellbosecke.pebble.utils.Operator;

/**
 * Parses expressions.
 */
public class ExpressionParser {

	private final Parser parser;
	private TokenStream stream;
	private Map<String, Operator> binaryOperators;
	private Map<String, Operator> unaryOperators;

	/**
	 * Constructor
	 * 
	 * @param parser
	 *            A reference to the main parser
	 */
	public ExpressionParser(Parser parser) {
		this.parser = parser;
		this.binaryOperators = parser.getEngine().getBinaryOperators();
		this.unaryOperators = parser.getEngine().getUnaryOperators();
	}

	/**
	 * The public entry point for parsing an expression.
	 * 
	 * @return NodeExpression the expression that has been parsed.
	 */
	public NodeExpression parseExpression() {
		return parseExpression(0);
	}

	/**
	 * A private entry point for parsing an expression. This method takes in the
	 * precedence required to operate a "precedence climbing" parsing algorithm.
	 * It is a recursive method.
	 * 
	 * @see http://en.wikipedia.org/wiki/Operator-precedence_parser
	 * 
	 * @return The NodeExpression representing the parsed expression.
	 */
	private NodeExpression parseExpression(int minPrecedence) {

		this.stream = parser.getStream();
		Token token = stream.current();
		NodeExpression expression;

		/*
		 * The first check is to see if the expression begins with a unary
		 * operator, or an opening bracket, or neither.
		 */

		if (isUnary(token)) {
			Operator operator = this.unaryOperators.get(token.getValue());
			stream.next();
			expression = parseExpression(operator.getPrecedence());

			// TODO: create unary expression

		} else if (token.test(Token.Type.PUNCTUATION, "(")) {

			stream.next();
			expression = parseExpression();
			stream.expect(Token.Type.PUNCTUATION, ")", "An opened parenthesis is not properly closed");
			return parsePostfixExpression(expression);

		} else {
			/*
			 * starts with neither. Let's parse out the first expression that we
			 * can find. There may be one, there may be many (separated by
			 * binary operators); right now we are just looking for the first.
			 */
			expression = subparseExpression();
		}

		/*
		 * If, after parsing the first expression we encounter a binary operator
		 * then we know we have another expression on the other side of the
		 * operator that requires parsing. Otherwise we're done.
		 */
		token = stream.current();
		while (isBinary(token) && binaryOperators.get(token.getValue()).getPrecedence() >= minPrecedence) {

			// find out which operator we are dealing with and then skip over it
			Operator operator = binaryOperators.get(token.getValue());
			stream.next();

			/*
			 * parse the expression on the right hand side of the operator while
			 * maintaining proper associativity and precedence
			 */
			NodeExpression expressionRight = parseExpression(Operator.Associativity.LEFT.equals(operator
					.getAssociativity()) ? operator.getPrecedence() + 1 : operator.getPrecedence());

			/*
			 * we have to wrap the left and right side expressions into one
			 * final expression. The operator provides us with the type of node
			 * we are creating (and an instance of that node type)
			 */
			NodeExpressionBinary finalExpression = (NodeExpressionBinary) operator.getNode();

			finalExpression.setLineNumber(stream.current().getLineNumber());
			finalExpression.setLeft(expression);
			finalExpression.setRight(expressionRight);

			expression = finalExpression;

			token = stream.current();
		}

		if (minPrecedence == 0) {
			return parseTernaryExpression(expression);
		}

		return expression;
	}

	/**
	 * Checks if a token is a unary operator.
	 * 
	 * @param token
	 *            The token that we are checking
	 * @return boolean Whether the token is a unary operator or not
	 */
	private boolean isUnary(Token token) {
		return token.test(Token.Type.OPERATOR) && this.unaryOperators.containsKey(token.getValue());
	}

	/**
	 * Checks if a token is a binary operator.
	 * 
	 * @param token
	 *            The token that we are checking
	 * @return boolean Whether the token is a binary operator or not
	 */
	private boolean isBinary(Token token) {
		return token.test(Token.Type.OPERATOR) && this.binaryOperators.containsKey(token.getValue());
	}

	/**
	 * Finds and returns the next "simple" expression; an expression of which
	 * can be found on either side of a binary operator but does not contain a
	 * binary operator. Ex. "var.field", "true", "12", etc.
	 * 
	 * @return NodeExpression The expression that it found.
	 */
	private NodeExpression subparseExpression() {
		Token token = stream.current();
		NodeExpression node = null;

		switch (token.getType()) {

			case NAME:
				/*
				 * The token can either be a constant such as true, false, or
				 * null or instead it might be the name of a variable or a
				 * function.
				 */
				switch (token.getValue()) {

					case "true":
					case "TRUE":
						node = new NodeExpressionConstant(true, token.getLineNumber());
						break;
					case "false":
					case "FALSE":
						node = new NodeExpressionConstant(false, token.getLineNumber());
						break;
					case "none":
					case "NONE":
					case "null":
					case "NULL":
						node = new NodeExpressionConstant(null, token.getLineNumber());
						break;

					default:
						// is it a method name?
						// TODO: check for function

						// it must be a variable name
						node = new NodeExpressionName(token.getLineNumber(), token.getValue());
						break;
				}
				break;

			case NUMBER:
				node = new NodeExpressionConstant(token.getValue(), token.getLineNumber());
				break;

			case STRING:
				node = new NodeExpressionConstant(token.getValue(), token.getLineNumber());
				break;

			// not found, syntax error
			default:
				throw new SyntaxException(String.format("Unexpected token \"%s\" of value \"%s\"", token.getType()
						.toString(), token.getValue()), token.getLineNumber(), stream.getFilename());
		}

		// there may or may not be more to this expression - let's keep looking
		stream.next();
		return parsePostfixExpression(node);
	}

	private NodeExpression parseTernaryExpression(NodeExpression expression) {
		while (this.stream.current().test(Token.Type.PUNCTUATION, "?")) {
			// TODO implement creating a ternary expression
		}

		return expression;
	}

	/**
	 * Determines if there is more to the provided expression than we originally
	 * thought. We will look for the filter operator or perhaps we are getting
	 * an attribute from a variable (ex. var.attribute).
	 * 
	 * @param node
	 *            The expression that we have already discovered
	 * @return Either the original expression that was passed in or a slightly
	 *         modified version of it, depending on what was discovered.
	 */
	private NodeExpression parsePostfixExpression(NodeExpression node) {
		Token current;
		while (true) {
			current = stream.current();

			// a period represents getting an attribute from a variable
			if (current.test(Token.Type.PUNCTUATION, ".")) {

				node = parseSubscriptExpression(node);

				// handle the filter operator
			} else if (current.test(Token.Type.PUNCTUATION, "|")) {

				// TODO: parse filter expression
				// node = parseFilterExpression(node);

			} else {
				break;
			}
		}
		return node;
	}

	/**
	 * A subscript expression can either be an expression getting an attribute
	 * from a variable, or calling a method from a variable, etc.
	 * 
	 * @param node
	 *            The expression parsed so far
	 * @return NodeExpression The parsed subscript expression
	 */
	private NodeExpression parseSubscriptExpression(NodeExpression node) {
		TokenStream stream = parser.getStream();
		Token token = stream.current();
		int lineNumber = token.getLineNumber();
		if (token.test(Token.Type.PUNCTUATION, ".")) {
			token = stream.next();
			if (token.test(Token.Type.NAME)) {
				NodeExpression constant = new NodeExpressionConstant(token.getValue(), token.getLineNumber());
				node = new NodeExpressionGetAttribute(node, constant, lineNumber);
				stream.next();
			}
		}
		return node;
	}

	public NodeExpressionArguments parseArguments() {
		List<NodeExpressionDeclaration> vars = new ArrayList<>();
		this.stream = this.parser.getStream();

		int lineNumber = stream.current().getLineNumber();

		stream.expect(Token.Type.PUNCTUATION, "(");

		while (!stream.current().test(Token.Type.PUNCTUATION, ")")) {

			if (!vars.isEmpty()) {
				stream.expect(Token.Type.PUNCTUATION, ",");
			}

			Token token = stream.current();
			vars.add(new NodeExpressionDeclaration(token.getLineNumber(), token.getValue()));
			
			stream.next();
		}

		stream.expect(Token.Type.PUNCTUATION, ")");

		return new NodeExpressionArguments(lineNumber, vars.toArray(new NodeExpressionDeclaration[vars.size()]));
	}

	public NodeExpressionDeclaration parseDeclarationExpression() {

		// set the stream because this function may be called externally
		this.stream = this.parser.getStream();
		Token token = stream.current();
		token.test(Token.Type.NAME);

		String[] reserved = new String[] { "true", "false", "null", "none" };
		if (Arrays.asList(reserved).contains(token.getValue())) {
			throw new SyntaxException(String.format("Can not assign a value to %s", token.getValue()),
					token.getLineNumber(), stream.getFilename());
		}

		stream.next();
		return new NodeExpressionDeclaration(token.getLineNumber(), token.getValue());
	}
}