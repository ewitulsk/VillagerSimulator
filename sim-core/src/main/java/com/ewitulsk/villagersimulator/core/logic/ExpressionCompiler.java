package com.ewitulsk.villagersimulator.core.logic;

import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.Expression;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The VS expression language v1 (docs/ARCHITECTURE.md §9): parse, type-check and compile to a tree of nodes, all at
 * load time. Evaluation allocates nothing.
 *
 * <pre>
 * expr     := or ('?' expr ':' expr)?
 * or       := and ('||' and)*
 * and      := eq ('&&' eq)*
 * eq       := cmp (('==' | '!=') cmp)*
 * cmp      := add (('<' | '<=' | '>' | '>=') add)*
 * add      := mul (('+' | '-') mul)*
 * mul      := unary (('*' | '/' | '%') unary)*
 * unary    := ('!' | '-') unary | primary
 * primary  := number | string | 'true' | 'false' | name '(' args? ')' | '(' expr ')'
 * </pre>
 *
 * Strings are {@code 'single'} or {@code "double"} quoted. Names may contain letters, digits, {@code _ . :}.
 */
public final class ExpressionCompiler {
    private final Map<String, ExpressionFunction> functions;

    public ExpressionCompiler(Map<String, ExpressionFunction> functions) {
        this.functions = functions;
    }

    public Expression compile(String source, ExprType expected) {
        Parser p = new Parser(source, tokenize(source));
        Node root = p.expr();
        p.expect(Tok.Kind.EOF, "end of expression");
        if (expected != null && root.type != expected) {
            throw new ExpressionException("Expected a " + expected.displayName() + " expression but got "
                    + root.type.displayName(), 0);
        }
        return new Compiled(source, root);
    }

    // ------------------------------------------------------------------------------------------------ tokens

    private record Tok(Kind kind, String text, double number, int pos) {
        enum Kind { NUMBER, STRING, NAME, OP, EOF }
    }

    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                String text = s.substring(start, i);
                try {
                    out.add(new Tok(Tok.Kind.NUMBER, text, Double.parseDouble(text), start));
                } catch (NumberFormatException e) {
                    throw new ExpressionException("Bad number '" + text + "'", start);
                }
            } else if (c == '\'' || c == '"') {
                int start = i++;
                StringBuilder b = new StringBuilder();
                while (i < s.length() && s.charAt(i) != c) b.append(s.charAt(i++));
                if (i >= s.length()) throw new ExpressionException("Unclosed string", start);
                i++;
                out.add(new Tok(Tok.Kind.STRING, b.toString(), 0, start));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || "_.:".indexOf(s.charAt(i)) >= 0)) i++;
                out.add(new Tok(Tok.Kind.NAME, s.substring(start, i), 0, start));
            } else {
                String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
                if (List.of("&&", "||", "==", "!=", "<=", ">=").contains(two)) {
                    out.add(new Tok(Tok.Kind.OP, two, 0, i));
                    i += 2;
                } else if ("+-*/%<>!?:(),".indexOf(c) >= 0) {
                    out.add(new Tok(Tok.Kind.OP, String.valueOf(c), 0, i));
                    i++;
                } else {
                    throw new ExpressionException("Unexpected character '" + c + "'", i);
                }
            }
        }
        out.add(new Tok(Tok.Kind.EOF, "", 0, s.length()));
        return out;
    }

    // ------------------------------------------------------------------------------------------------ nodes

    /** A typed, compiled node. Only the method matching {@link #type} is called. */
    abstract static class Node {
        final ExprType type;
        final int pos;

        Node(ExprType type, int pos) {
            this.type = type;
            this.pos = pos;
        }

        double num(ExprEnv env) {
            throw new IllegalStateException("not a number");
        }

        boolean bool(ExprEnv env) {
            throw new IllegalStateException("not a bool");
        }

        String str(ExprEnv env) {
            throw new IllegalStateException("not a string");
        }

        /** Literal string value, or null. */
        String constant() {
            return null;
        }
    }

    private static Node number(double v, int pos) {
        return new Node(ExprType.NUMBER, pos) {
            @Override
            double num(ExprEnv env) {
                return v;
            }
        };
    }

    private static Node bool(boolean v, int pos) {
        return new Node(ExprType.BOOL, pos) {
            @Override
            boolean bool(ExprEnv env) {
                return v;
            }
        };
    }

    private static Node string(String v, int pos) {
        return new Node(ExprType.STRING, pos) {
            @Override
            String str(ExprEnv env) {
                return v;
            }

            @Override
            String constant() {
                return v;
            }
        };
    }

    private record Compiled(String source, Node root) implements Expression {
        @Override
        public ExprType type() {
            return root.type;
        }

        @Override
        public double number(ExprEnv env) {
            return root.num(env);
        }

        @Override
        public boolean bool(ExprEnv env) {
            return root.bool(env);
        }

        @Override
        public String string(ExprEnv env) {
            return root.str(env);
        }
    }

    // ------------------------------------------------------------------------------------------------ parser

    private final class Parser {
        private final String source;
        private final List<Tok> toks;
        private int at;

        Parser(String source, List<Tok> toks) {
            this.source = source;
            this.toks = toks;
        }

        private Tok peek() {
            return toks.get(at);
        }

        private boolean op(String text) {
            Tok t = peek();
            if (t.kind() == Tok.Kind.OP && t.text().equals(text)) {
                at++;
                return true;
            }
            return false;
        }

        Tok expect(Tok.Kind kind, String what) {
            Tok t = peek();
            if (t.kind() != kind) throw new ExpressionException("Expected " + what + " but found " + describe(t), t.pos());
            at++;
            return t;
        }

        private void expectOp(String text) {
            Tok t = peek();
            if (!op(text)) throw new ExpressionException("Expected '" + text + "' but found " + describe(t), t.pos());
        }

        private String describe(Tok t) {
            return t.kind() == Tok.Kind.EOF ? "end of expression" : "'" + t.text() + "'";
        }

        private void need(Node n, ExprType type, String where) {
            if (n.type != type) {
                throw new ExpressionException(where + " needs a " + type.displayName() + " but got a " + n.type.displayName(), n.pos);
            }
        }

        Node expr() {
            Node cond = or();
            int pos = peek().pos();
            if (!op("?")) return cond;
            need(cond, ExprType.BOOL, "'?'");
            Node a = expr();
            expectOp(":");
            Node b = expr();
            if (a.type != b.type) {
                throw new ExpressionException("Both branches of '?:' must have the same type (" + a.type.displayName()
                        + " vs " + b.type.displayName() + ")", b.pos);
            }
            return switch (a.type) {
                case NUMBER -> new Node(ExprType.NUMBER, pos) {
                    @Override
                    double num(ExprEnv env) {
                        return cond.bool(env) ? a.num(env) : b.num(env);
                    }
                };
                case BOOL -> new Node(ExprType.BOOL, pos) {
                    @Override
                    boolean bool(ExprEnv env) {
                        return cond.bool(env) ? a.bool(env) : b.bool(env);
                    }
                };
                case STRING -> new Node(ExprType.STRING, pos) {
                    @Override
                    String str(ExprEnv env) {
                        return cond.bool(env) ? a.str(env) : b.str(env);
                    }
                };
            };
        }

        private Node or() {
            Node left = and();
            while (true) {
                int pos = peek().pos();
                if (!op("||")) return left;
                Node l = left, r = and();
                need(l, ExprType.BOOL, "'||'");
                need(r, ExprType.BOOL, "'||'");
                left = new Node(ExprType.BOOL, pos) {
                    @Override
                    boolean bool(ExprEnv env) {
                        return l.bool(env) || r.bool(env);
                    }
                };
            }
        }

        private Node and() {
            Node left = eq();
            while (true) {
                int pos = peek().pos();
                if (!op("&&")) return left;
                Node l = left, r = eq();
                need(l, ExprType.BOOL, "'&&'");
                need(r, ExprType.BOOL, "'&&'");
                left = new Node(ExprType.BOOL, pos) {
                    @Override
                    boolean bool(ExprEnv env) {
                        return l.bool(env) && r.bool(env);
                    }
                };
            }
        }

        private Node eq() {
            Node left = cmp();
            while (true) {
                int pos = peek().pos();
                boolean equal;
                if (op("==")) equal = true;
                else if (op("!=")) equal = false;
                else return left;
                Node l = left, r = cmp();
                if (l.type != r.type) {
                    throw new ExpressionException("Can't compare a " + l.type.displayName() + " with a " + r.type.displayName(), pos);
                }
                left = switch (l.type) {
                    case NUMBER -> new Node(ExprType.BOOL, pos) {
                        @Override
                        boolean bool(ExprEnv env) {
                            return (l.num(env) == r.num(env)) == equal;
                        }
                    };
                    case BOOL -> new Node(ExprType.BOOL, pos) {
                        @Override
                        boolean bool(ExprEnv env) {
                            return (l.bool(env) == r.bool(env)) == equal;
                        }
                    };
                    case STRING -> new Node(ExprType.BOOL, pos) {
                        @Override
                        boolean bool(ExprEnv env) {
                            return l.str(env).equals(r.str(env)) == equal;
                        }
                    };
                };
            }
        }

        private Node cmp() {
            Node left = add();
            while (true) {
                int pos = peek().pos();
                String o;
                if (op("<=")) o = "<=";
                else if (op(">=")) o = ">=";
                else if (op("<")) o = "<";
                else if (op(">")) o = ">";
                else return left;
                Node l = left, r = add();
                need(l, ExprType.NUMBER, "'" + o + "'");
                need(r, ExprType.NUMBER, "'" + o + "'");
                left = switch (o) {
                    case "<" -> new Node(ExprType.BOOL, pos) {
                        @Override
                        boolean bool(ExprEnv env) {
                            return l.num(env) < r.num(env);
                        }
                    };
                    case "<=" -> new Node(ExprType.BOOL, pos) {
                        @Override
                        boolean bool(ExprEnv env) {
                            return l.num(env) <= r.num(env);
                        }
                    };
                    case ">" -> new Node(ExprType.BOOL, pos) {
                        @Override
                        boolean bool(ExprEnv env) {
                            return l.num(env) > r.num(env);
                        }
                    };
                    default -> new Node(ExprType.BOOL, pos) {
                        @Override
                        boolean bool(ExprEnv env) {
                            return l.num(env) >= r.num(env);
                        }
                    };
                };
            }
        }

        private Node add() {
            Node left = mul();
            while (true) {
                int pos = peek().pos();
                boolean plus;
                if (op("+")) plus = true;
                else if (op("-")) plus = false;
                else return left;
                Node l = left, r = mul();
                need(l, ExprType.NUMBER, plus ? "'+'" : "'-'");
                need(r, ExprType.NUMBER, plus ? "'+'" : "'-'");
                left = plus ? new Node(ExprType.NUMBER, pos) {
                    @Override
                    double num(ExprEnv env) {
                        return l.num(env) + r.num(env);
                    }
                } : new Node(ExprType.NUMBER, pos) {
                    @Override
                    double num(ExprEnv env) {
                        return l.num(env) - r.num(env);
                    }
                };
            }
        }

        private Node mul() {
            Node left = unary();
            while (true) {
                int pos = peek().pos();
                String o;
                if (op("*")) o = "*";
                else if (op("/")) o = "/";
                else if (op("%")) o = "%";
                else return left;
                Node l = left, r = unary();
                need(l, ExprType.NUMBER, "'" + o + "'");
                need(r, ExprType.NUMBER, "'" + o + "'");
                left = switch (o) {
                    case "*" -> new Node(ExprType.NUMBER, pos) {
                        @Override
                        double num(ExprEnv env) {
                            return l.num(env) * r.num(env);
                        }
                    };
                    // Division by zero yields 0 rather than infinity/NaN, so bad data can't poison scores.
                    case "/" -> new Node(ExprType.NUMBER, pos) {
                        @Override
                        double num(ExprEnv env) {
                            double d = r.num(env);
                            return d == 0 ? 0 : l.num(env) / d;
                        }
                    };
                    default -> new Node(ExprType.NUMBER, pos) {
                        @Override
                        double num(ExprEnv env) {
                            double d = r.num(env);
                            return d == 0 ? 0 : l.num(env) % d;
                        }
                    };
                };
            }
        }

        private Node unary() {
            int pos = peek().pos();
            if (op("!")) {
                Node n = unary();
                need(n, ExprType.BOOL, "'!'");
                return new Node(ExprType.BOOL, pos) {
                    @Override
                    boolean bool(ExprEnv env) {
                        return !n.bool(env);
                    }
                };
            }
            if (op("-")) {
                Node n = unary();
                need(n, ExprType.NUMBER, "'-'");
                return new Node(ExprType.NUMBER, pos) {
                    @Override
                    double num(ExprEnv env) {
                        return -n.num(env);
                    }
                };
            }
            return primary();
        }

        private Node primary() {
            Tok t = peek();
            switch (t.kind()) {
                case NUMBER -> {
                    at++;
                    return number(t.number(), t.pos());
                }
                case STRING -> {
                    at++;
                    return string(t.text(), t.pos());
                }
                case NAME -> {
                    at++;
                    if (t.text().equals("true")) return bool(true, t.pos());
                    if (t.text().equals("false")) return bool(false, t.pos());
                    return call(t);
                }
                default -> {
                    if (op("(")) {
                        Node n = expr();
                        expectOp(")");
                        return n;
                    }
                    throw new ExpressionException("Unexpected " + describe(t), t.pos());
                }
            }
        }

        private Node call(Tok name) {
            ExpressionFunction fn = functions.get(name.text());
            if (fn == null) throw new ExpressionException("Unknown function '" + name.text() + "'", name.pos());
            if (!op("(")) throw new ExpressionException("'" + name.text() + "' is a function: write " + name.text() + "(...)", name.pos());
            List<Node> args = new ArrayList<>();
            if (!op(")")) {
                do {
                    args.add(expr());
                } while (op(","));
                expectOp(")");
            }
            if (args.size() != fn.params().size()) {
                throw new ExpressionException(fn.signature() + " takes " + fn.params().size() + " argument(s), got " + args.size(), name.pos());
            }
            for (int i = 0; i < args.size(); i++) {
                need(args.get(i), fn.params().get(i), "Argument " + (i + 1) + " of " + fn.name());
            }
            Node[] a = args.toArray(Node[]::new);
            ExpressionFunction.Args argv = new ExpressionFunction.Args() {
                @Override
                public double number(int i, ExprEnv env) {
                    return a[i].num(env);
                }

                @Override
                public boolean bool(int i, ExprEnv env) {
                    return a[i].bool(env);
                }

                @Override
                public String string(int i, ExprEnv env) {
                    return a[i].str(env);
                }

                @Override
                public String constant(int i) {
                    return a[i].constant();
                }
            };
            ExpressionFunction.Impl impl = fn.impl();
            return switch (fn.result()) {
                case NUMBER -> new Node(ExprType.NUMBER, name.pos()) {
                    @Override
                    double num(ExprEnv env) {
                        return impl.number(env, argv);
                    }
                };
                case BOOL -> new Node(ExprType.BOOL, name.pos()) {
                    @Override
                    boolean bool(ExprEnv env) {
                        return impl.bool(env, argv);
                    }
                };
                case STRING -> new Node(ExprType.STRING, name.pos()) {
                    @Override
                    String str(ExprEnv env) {
                        return impl.string(env, argv);
                    }
                };
            };
        }
    }
}
