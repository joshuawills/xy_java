package compiler;
import java.util.ArrayList;
import java.util.HashMap;

import compiler.nodes.NodeFunction;
import compiler.nodes.NodeParameters;
import compiler.nodes.NodeProgram;
import compiler.nodes.expression_nodes.NodeExpression;
import compiler.nodes.expression_nodes.binary_nodes.BinaryExpression;
import compiler.nodes.expression_nodes.term_nodes.FuncCallNode;
import compiler.nodes.expression_nodes.term_nodes.IdentExpression;
import compiler.nodes.expression_nodes.term_nodes.IntLitExpression;
import compiler.nodes.expression_nodes.term_nodes.NegationExpression;
import compiler.nodes.expression_nodes.term_nodes.NodeTerm;
import compiler.nodes.expression_nodes.term_nodes.ParenExpression;
import compiler.nodes.expression_nodes.term_nodes.StringExpression;
import compiler.nodes.statement_nodes.NodeAssign;
import compiler.nodes.statement_nodes.NodeLet;
import compiler.nodes.statement_nodes.NodePrint;
import compiler.nodes.statement_nodes.NodeReturn;
import compiler.nodes.statement_nodes.NodeScan;
import compiler.nodes.statement_nodes.NodeScope;
import compiler.nodes.statement_nodes.NodeStatement;
import compiler.nodes.statement_nodes.conditionals.NodeIf;
import compiler.nodes.statement_nodes.conditionals.NodeIfPredicate;
import compiler.nodes.statement_nodes.conditionals.NodeIfPredicateElif;
import compiler.nodes.statement_nodes.conditionals.NodeIfPredicateElse;
import compiler.nodes.statement_nodes.loops.NodeBreak;
import compiler.nodes.statement_nodes.loops.NodeContinue;
import compiler.nodes.statement_nodes.loops.NodeDo;
import compiler.nodes.statement_nodes.loops.NodeLoop;
import compiler.nodes.statement_nodes.loops.NodeWhile;

public class Parser {

    private final ArrayList<Token> tokens;
    private int iterator = 0;
    private HashMap<String, String> configSettings = new HashMap<>();

    public Parser(ArrayList<Token> tokens, HashMap<String, String> configSettings) {
        this.tokens = tokens;
        this.configSettings = configSettings;
    }

    public NodeProgram parseProgram() {

        NodeProgram program = new NodeProgram();
        while (peek() != null)
            program.appendFunction(parseFunction());
        return program;
    }
    
    private NodeParameters parseParameters() {
        NodeParameters parameters = new NodeParameters();
        expect(TokenType.OPEN_PAREN);

        if (tryConsume(TokenType.CLOSE_PAREN) != null)
            return parameters;
    
        while (true) {
            TokenType type = expect(TokenType.INT).getType();
            String name = expect(TokenType.IDENT).getValue();
            if (tryConsume(TokenType.CLOSE_PAREN) != null) {
                parameters.addVariable(name, type);
                break;
            }
            expect(TokenType.COMMA);
            parameters.addVariable(name, type);
        }
        return parameters;
    }

    private NodeFunction parseFunction() {
        expect(TokenType.DEFINE);
        String functionName = expect(TokenType.IDENT).getValue();
        NodeParameters parameters = parseParameters();

        expect(TokenType.RETURN_SPEC);
        Token returnType = expect(TokenType.INT);
        NodeScope scope = parseScope();
        return new NodeFunction(scope, functionName, returnType, parameters);
    }


    private NodeTerm parseTerm() {
        
        if (peek() != null && peek().getType().equals(TokenType.INT_LIT)) {
            return new IntLitExpression(consume());

        } else if (peek() != null && peek().getType().equals(TokenType.OPEN_PAREN)) {

            consume();
            NodeExpression expression = parseExpression(0);
            if (expression == null)
                Error.handleError("PARSING", "Expected expression");
            expect(TokenType.CLOSE_PAREN);
            return new ParenExpression(expression);

        } else if (peek() != null && peek().getType().equals(TokenType.NEGATE)) {

            consume();
            NodeExpression expression = parseTerm();
            if (expression == null)
                Error.handleError("PARSING", "Expected expression");
            return new NegationExpression(expression);
        
        } else if (peek() != null && peek().getType().equals(TokenType.IDENT) && peek(1) != null && peek(1).getType().equals(TokenType.OPEN_PAREN)) {
            
            String funcName = consume().getValue();
            expect(TokenType.OPEN_PAREN);
            if (tryConsume(TokenType.CLOSE_PAREN) != null) {
                return new FuncCallNode(funcName, new ArrayList<NodeTerm>());
            }

            ArrayList<NodeTerm> parameters = new ArrayList<>();
            while (true) {
                NodeTerm term = parseTerm();
                parameters.add(term);
                if (term == null)
                    Error.handleError("PARSING", "Unable to parse term");
                if (tryConsume(TokenType.COMMA) != null) {
                    continue;
                } else if (tryConsume(TokenType.CLOSE_PAREN) != null) {
                    return new FuncCallNode(funcName, parameters);
                } else {
                    Error.handleError("PARSING", "Unexpected token" + consume().toString());
                }
            }


        } else if (peek() != null && peek().getType().equals(TokenType.IDENT)) { // int x = 5;

            return new IdentExpression(consume());
        
        }
        return null;
    }

    private NodeExpression parseExpression(int minimumPrecedence) { // set to 0 as default
        NodeExpression lhs = parseTerm();
        if (lhs == null) return null;
        Integer precedenceLevel;
        while (true) {
            Token currentToken = peek();
            if (currentToken == null) break;
            precedenceLevel = Token.getBinaryPrecedenceLevel(currentToken.getType());
            if (precedenceLevel == null || precedenceLevel < minimumPrecedence) break;

            Token operator = consume();
            int nextMinPrec = precedenceLevel + 1;
            NodeExpression rhs = parseExpression(nextMinPrec);
            if (rhs == null)
                Error.handleError("PARSING", "Unable to parse expression");

            BinaryExpression myExpression = new BinaryExpression();
            switch (operator.getType()) {
                case GREATER_EQ:
                case GREATER_THAN:
                case LESS_THAN:
                case LESS_EQ:
                case EQUAL:
                case NOT_EQUAL:
                case AND_LOGIC:
                case OR_LOGIC:
                case PLUS:
                case STAR:
                case DASH:
                case F_SLASH:
                case PERCENT:
                case BITWISE_AND:
                case BITWISE_LEFT_SHIFT:
                case BITWISE_OR:
                case BITWISE_RIGHT_SHIFT:
                case BITWISE_XOR:
                    myExpression.setOperator(operator.getType());
                    break;
                default:
                    Error.handleError("PARSING", "Unknown operator");
            }
            
            myExpression.setLHS(lhs);
            myExpression.setRHS(rhs);
            lhs = myExpression;
            
        }
        return lhs;
    }

    private Token tryConsume(TokenType type) {
        if (peek() != null && peek().getType().equals(type)) {
            Token currentToken = peek();
            consume();
            return currentToken;
        }
        return null;
    }

    private boolean isPeek(TokenType type) {
        return peek() != null && peek().getType().equals(type);
    }


    private NodeStatement parseStatement() {
        if (tryConsume(TokenType.RETURN) != null) {

            NodeExpression expression = parseExpression(0);
            if (expression == null) {
                if (peek() == null)
                    Error.handleError("Parsing", "Invalid expression near EOF");
                else
                    Error.handleError("Parsing", "Invalid expression\n    line: " + peek(-1).getLine() + ", col: " + peek(-1).getCol());
            }
            expect(TokenType.SEMI);
            return new NodeReturn(expression);

        } else if (tryConsume(TokenType.INT) != null) { // int x = 32;

            Token ident = expect(TokenType.IDENT);
            expect(TokenType.ASSIGN);

            if (tryConsume(TokenType.IN) != null) {
                String value = expect(TokenType.STRING).getValue();
                expect(TokenType.SEMI);
                return new NodeScan(value, ident, true);
            }
            NodeExpression expression = parseExpression(0);
            expect(TokenType.SEMI);
            return new NodeLet(ident, expression, true);

        } else if (tryConsume(TokenType.MUT) != null) {

            expect(TokenType.INT);
            Token ident = expect(TokenType.IDENT);
            expect(TokenType.ASSIGN);

            if (tryConsume(TokenType.IN) != null) {
                String value = expect(TokenType.STRING).getValue();
                expect(TokenType.SEMI);
                return new NodeScan(value, ident, false);
            }

            NodeExpression expression = parseExpression(0);
            expect(TokenType.SEMI);
            return new NodeLet(ident, expression, false);

        }
        else if (tryConsume(TokenType.IF) != null) { // if () {}

            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_PAREN))
                Error.minorError("CONFIG-SPECIFIC", "Missing () around if statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            NodeExpression expression = parseExpression(0);
            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_CURLY))
                Error.minorError("CONFIG-SPECIFIC", "Missing {} around if statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            return new NodeIf(expression, parseScope(), parseIfPred());

        } else if (tryConsume(TokenType.WHILE) != null) {
            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_PAREN))
                Error.minorError("CONFIG-SPECIFIC", "Missing () around while statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            NodeExpression expression = parseExpression(0);
            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_CURLY))
                Error.minorError("CONFIG-SPECIFIC", "Missing {} around while statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            return new NodeWhile(expression, parseScope());

        } else if (tryConsume(TokenType.LOOP) != null) {

            NodeScope scope = parseScope();
            return new NodeLoop(scope);

        } else if (tryConsume(TokenType.DO) != null) {

            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_CURLY))
                    Error.minorError("CONFIG-SPECIFIC", "Missing {} around do-while statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            NodeScope scope = parseScope();
            expect(TokenType.WHILE);
            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_PAREN))
                    Error.minorError("CONFIG-SPECIFIC", "Missing () around do-while statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            NodeExpression expression = parseExpression(0);
            expect(TokenType.SEMI);
            return new NodeDo(expression, scope);

        } else if (isPeek(TokenType.IDENT)) {
            Token ident = expect(TokenType.IDENT);

            NodeExpression expression = null;
            NodeExpression lhs = new IdentExpression(ident);
            if (tryConsume(TokenType.INCREMENT) != null) { // i++;
                NodeExpression rhs = new IntLitExpression(new Token(TokenType.INT_LIT, "1", ident.getLine(), ident.getCol()));
                expression = new BinaryExpression(lhs, rhs, TokenType.PLUS);
            } else if (tryConsume(TokenType.DECREMENT) != null) { // i--;
                NodeExpression rhs = new IntLitExpression(new Token(TokenType.INT_LIT, "1", ident.getLine(), ident.getCol()));
                expression = new BinaryExpression(lhs, rhs, TokenType.DASH);
            } else if (tryConsume(TokenType.PLUS_EQUAL) != null) {
                NodeExpression rhs = parseExpression(0);
                expression = new BinaryExpression(lhs, rhs, TokenType.PLUS);
            } else if (tryConsume(TokenType.DASH_EQUAL) != null) {
                NodeExpression rhs = parseExpression(0);
                expression = new BinaryExpression(lhs, rhs, TokenType.DASH);
            } else if (tryConsume(TokenType.STAR_EQUAL) != null) {
                NodeExpression rhs = parseExpression(0);
                expression = new BinaryExpression(lhs, rhs, TokenType.STAR);
            } else if (tryConsume(TokenType.F_SLASH_EQUAL) != null) {
                NodeExpression rhs = parseExpression(0);
                expression = new BinaryExpression(lhs, rhs, TokenType.F_SLASH);
            } 
            
            else {
                expect(TokenType.ASSIGN);
                expression = parseExpression(0);
            }
            expect(TokenType.SEMI);
            return new NodeAssign(ident, expression);

        }  else if (isPeek(TokenType.OPEN_CURLY)) { // Entered a scope

            return parseScope();

        } else if (tryConsume(TokenType.OUT) != null) {

            if (isPeek(TokenType.STRING)) {
                NodeTerm stringToken = new StringExpression(consume());
                expect(TokenType.SEMI);
                return new NodePrint(stringToken, true);
            } 
            NodeTerm term = parseTerm(); // reconsider when different terms are added
            expect(TokenType.SEMI);
            return new NodePrint(term);

        } else if (tryConsume(TokenType.CONTINUE) != null) {
            expect(TokenType.SEMI);
            return new NodeContinue();
        } else if (tryConsume(TokenType.BREAK) != null) {

            expect(TokenType.SEMI);
            return new NodeBreak();
        
        }
        return null;
    }

    private NodeScope parseScope() {
        NodeScope scope = new NodeScope();
        if (tryConsume(TokenType.OPEN_CURLY) != null) {
            NodeStatement statement = parseStatement();
            while (statement != null) {
                scope.addStatement(statement);
                statement = parseStatement();
            }
            expect(TokenType.CLOSE_CURLY);
        } else {
            NodeStatement statement = parseStatement();
            scope.addStatement(statement);
        }
        return scope;
    }

    private NodeIfPredicate parseIfPred() {
        Token currentToken = peek();
        if (currentToken != null && currentToken.getType().equals(TokenType.ELIF)) {
            consume();
            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_PAREN))
                Error.minorError("CONFIG-SPECIFIC", "Missing () around elif statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            NodeExpression expression = parseExpression(0);
            if (expression == null) {
                if (peek() == null) {
                    Error.handleError("Parsing", "Unable to parse expression");
                } else {
                    Error.handleError("Parsing", "Unable to parse expression\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
                }
            }
            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_CURLY))
                Error.minorError("CONFIG-SPECIFIC", "Missing {} around elif statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            return new NodeIfPredicateElif(expression, parseScope(), parseIfPred());
        } else if (currentToken != null && currentToken.getType().equals(TokenType.ELSE)) {
            consume();
            if (configSettings.containsKey("MANDATE-BRACKETS") && configSettings.get("MANDATE-BRACKETS").equals("true")) {
                if (peek() != null && !peek().getType().equals(TokenType.OPEN_CURLY))
                Error.minorError("CONFIG-SPECIFIC", "Missing {} around else statement\n    line: " + peek(0).getLine() + ", col: " + peek(0).getCol());
            }
            return new NodeIfPredicateElse(parseScope());
        }

        return null;
    }

    private Token expect(TokenType type) {
        Token currentToken = peek();
        if (currentToken == null)
            Error.handleError("Parsing", String.format("Expected %s, Received nothing", type).concat("\n    line: " + peek().getLine() + ", col: " + peek().getCol()));
        if (!currentToken.getType().equals(type)) {
            Error.handleError("Parsing", String.format("Expected %s, Received %s", type, currentToken.getType()).concat("\n    line: " + peek().getLine() + ", col: " + peek().getCol()));
        }
        this.iterator++;
        return currentToken;
    }

    private Token consume() {
        if (this.iterator >= tokens.size())
            return null;
        Token current = this.tokens.get(this.iterator);
        this.iterator++;
        return current;
    }

    private Token peek() {
        if (this.iterator >= tokens.size())
            return null;
        return this.tokens.get(this.iterator);
    }

    private Token peek(int var) {
        if (this.iterator + var < 0 || this.iterator + var >= tokens.size())
            return null;
        return this.tokens.get(this.iterator + var);
    }


}
