package compiler.nodes.expression_nodes.term_nodes;

import compiler.Generator;
import compiler.Verifier;

public class ItExpression extends NodeTerm {
    
    private Integer loopDepth;

    @Override
    public String toString() {
        return "it";
    }

    public void setDepth(Integer depth) {
        this.loopDepth = depth;
    }

    public String getType(Verifier v) {
        return "it";
    }

    public void operator(Generator generator) {
        generator.appendContents("__lc__" + loopDepth + "_");
    }

}
