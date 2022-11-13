package analyzer.visitors;

import analyzer.ast.*;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.awt.Symbol;

import java.awt.*;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Vector;


/**
 * Created: 19-02-15
 * Last Changed: 20-10-6
 * Author: Félix Brunet & Doriane Olewicki
 * Modified by: Gérard Akkerhuis
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class IntermediateCodeGenFallVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private final PrintWriter m_writer;

    public IntermediateCodeGenFallVisitor(PrintWriter writer) {
        m_writer = writer;
    }
    public HashMap<String, VarType> SymbolTable = new HashMap<>();

    private int id = 0;
    private int label = 0;
    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genId() {
        return "_t" + id++;
    }

    //génère un nouveau Label qu'il est possible de print.
    private String genLabel() {
        return "_L" + label++;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data)  {
        String s_next = genLabel();
        node.childrenAccept(this, s_next);
        m_writer.println(s_next);
        return null;
    }

    /*
    Code fournis pour remplir la table de symbole.
    Les déclarations ne sont plus utile dans le code à trois adresse.
    elle ne sont donc pas concervé.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        ASTIdentifier id = (ASTIdentifier) node.jjtGetChild(0);
        VarType t;
        if (node.getValue().equals("bool")) {
            t = VarType.Bool;
        } else {
            t = VarType.Number;
        }
        SymbolTable.put(id.getValue(), t);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        if (node.jjtGetNumChildren() == 1)
            node.jjtGetChild(0).jjtAccept(this, data);
        else {
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                if (i != node.jjtGetNumChildren() - 1) {
                    String s_next = genLabel();
                    node.jjtGetChild(i).jjtAccept(this, s_next);
                    m_writer.println(s_next);
                } else
                    node.jjtGetChild(i).jjtAccept(this, data);
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        BoolLabel bl = new BoolLabel(null, null);
        switch(node.jjtGetNumChildren()) {
            case 2: // if (B) S
                bl.lTrue = "fall";
                bl.lFalse = (String) data;
                node.jjtGetChild(0).jjtAccept(this, bl);
                node.jjtGetChild(1).jjtAccept(this, data);
                break;
            case 3: // if (B) S1 else S2
                bl.lTrue = "fall";
                bl.lFalse = genLabel();
                node.jjtGetChild(0).jjtAccept(this, bl);
                node.jjtGetChild(1).jjtAccept(this, data);
                m_writer.println("goto " + data);
                m_writer.println(bl.lFalse);
                node.jjtGetChild(2).jjtAccept(this, data);
                break;
            default:
                break;
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String begin = genLabel();
        BoolLabel bl = new BoolLabel("fall", (String) data);
        m_writer.println(begin);
        node.jjtGetChild(0).jjtAccept(this, bl);
        node.jjtGetChild(1).jjtAccept(this, begin);
        m_writer.println("goto " + begin);
        return null;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String id = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if (SymbolTable.get(id) == VarType.Bool) {
            BoolLabel bl = new BoolLabel("fall", genLabel());
            node.jjtGetChild(1).jjtAccept(this, bl);
            m_writer.println(id + " = 1");
            m_writer.println("goto " + data);
            m_writer.println(bl.lFalse);
            m_writer.println(id + " = 0");
        } else {
            String addr = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println(id + " = " + addr);
        }
        return id;
    }

    @Override
    public Object visit(ASTExpr node, Object data){
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    //Expression arithmétique
    /*
    Les expressions arithmétique add et mult fonctionne exactement de la même manière. c'est pourquoi
    il est plus simple de remplir cette fonction une fois pour avoir le résultat pour les deux noeuds.

    On peut bouclé sur "ops" ou sur node.jjtGetNumChildren(),
    la taille de ops sera toujours 1 de moins que la taille de jjtGetNumChildren
     */
    public Object codeExtAddMul(SimpleNode node, Object data, Vector<String> ops) {
        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        String id = "";
        String tac = ""; // Three-Adress Code

        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            if (i % 2 == 0) {
                id = genId();
                String result = (String) node.jjtGetChild(i).jjtAccept(this, data);
                tac += id + " = " + result;
            } else {
                tac += " " + ops.get(0) + " " + node.jjtGetChild(i).jjtAccept(this, data);
                m_writer.println(tac);
                tac = "";
            }
        }
        return id;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    //UnaExpr est presque pareil au deux précédente. la plus grosse différence est qu'il ne va pas
    //chercher un deuxième noeud enfant pour avoir une valeur puisqu'il s'agit d'une opération unaire.
    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        if (node.getOps().isEmpty())
            return node.jjtGetChild(0).jjtAccept(this, data);
        String addr = (String) node.jjtGetChild(0).jjtAccept(this, data);
        for (int i = 0; i < node.getOps().size(); i++) {
            String id = genId();
            m_writer.println(id + " = " + node.getOps().get(i) + " " + addr);
            addr = id;
        }
        return addr;
    }

    //expression logique
    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        Vector ops = node.getOps();
        if (ops.size() == 0)
            return node.jjtGetChild(0).jjtAccept(this, data);

        for (int i = 0; i < ops.size(); i++) {
            BoolLabel bl = (BoolLabel) data;
            BoolLabel bl1 = new BoolLabel(null, null);
            BoolLabel bl2 = new BoolLabel(null, null);
            switch (ops.get(i).toString()) {
                case "&&":
                    bl1.lTrue = "fall";
                    if (bl.lFalse == "fall")
                        bl1.lFalse = genLabel();
                    else
                        bl1.lFalse = bl.lFalse;
                    bl2.lTrue = bl.lTrue;
                    bl2.lFalse = bl.lFalse;
                    if (bl.lFalse == "fall"){
                        node.jjtGetChild(0).jjtAccept(this, bl1);
                        node.jjtGetChild(1).jjtAccept(this, bl2);
                        m_writer.println(bl1.lFalse);
                    } else {
                        node.jjtGetChild(0).jjtAccept(this, bl1);
                        node.jjtGetChild(1).jjtAccept(this, bl2);
                    }
                    break;
                case "||":
                    if (bl.lTrue == "fall")
                        bl1.lTrue = genLabel();
                    else
                        bl1.lTrue = bl.lTrue;

                    bl1.lFalse = "fall";
                    bl2.lTrue = bl.lTrue;
                    bl2.lFalse = bl.lFalse;
                    if (bl.lTrue == "fall"){
                        node.jjtGetChild(0).jjtAccept(this, bl1);
                        node.jjtGetChild(1).jjtAccept(this, bl2);

                        m_writer.println(bl1.lTrue);
                    } else {
                        node.jjtGetChild(0).jjtAccept(this, bl1);
                        node.jjtGetChild(1).jjtAccept(this, bl2);
                    }
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        if (node.jjtGetNumChildren() == 1)
            return node.jjtGetChild(0).jjtAccept(this, data);

        BoolLabel bl = (BoolLabel) data;

        if (bl.lTrue != "fall" && bl.lFalse != "fall") {
            m_writer.println(
                    "if " + node.jjtGetChild(0).jjtAccept(this, data) + " " + node.getValue() + " "
                    + node.jjtGetChild(1).jjtAccept(this, data) + " goto " + bl.lTrue
            );
            m_writer.println("goto " + bl.lFalse);
        } else if (bl.lTrue != "fall" && bl.lFalse == "fall") {
            m_writer.println(
                    "if " + node.jjtGetChild(0).jjtAccept(this, data) + " " + node.getValue() + " "
                    + node.jjtGetChild(1).jjtAccept(this, data) + " goto " + bl.lTrue
            );
        } else if (bl.lTrue == "fall" && bl.lFalse != "fall") {
            m_writer.println(
                    "ifFalse " + node.jjtGetChild(0).jjtAccept(this, data) + " " + node.getValue() + " "
                    + node.jjtGetChild(1).jjtAccept(this, data) + " goto " + bl.lFalse
            );
        }
        return null;
    }


    /*
    Même si on peut y avoir un grand nombre d'opération, celle-ci s'annullent entre elle.
    il est donc intéressant de vérifier si le nombre d'opération est pair ou impaire.
    Si le nombre d'opération est pair, on peut simplement ignorer ce noeud.
     */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        if (node.getOps().size() % 2 == 0)
            return node.jjtGetChild(0).jjtAccept(this, data);

        BoolLabel bl = (BoolLabel) data;
        BoolLabel bl1 = new BoolLabel(bl.lFalse, bl.lTrue);
        return node.jjtGetChild(0).jjtAccept(this, bl1);
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    /*
    BoolValue ne peut pas simplement retourné sa valeur à son parent contrairement à GenValue et IntValue,
    Il doit plutôt généré des Goto direct, selon sa valeur.
     */
    @Override
    public Object visit(ASTBoolValue node, Object data) {
        if (data != null) {
            BoolLabel bl = (BoolLabel) data;
            if (node.getValue()){
                if (bl.lTrue != "fall")
                    m_writer.println("goto " + bl.lTrue);
            } else {
                if (bl.lFalse != "fall")
                    m_writer.println("goto " + bl.lFalse);
            }
        }
        return node.getValue().toString();
    }

    /*
    si le type de la variable est booléenne, il faudra généré des goto ici.
    le truc est de faire un "if value == 1 goto Label".
    en effet, la structure "if valeurBool goto Label" n'existe pas dans la syntaxe du code à trois adresse.
     */
    @Override
    public Object visit(ASTIdentifier node, Object data) {
        VarType type = SymbolTable.get(node.getValue());
        if (type == VarType.Bool){
            BoolLabel bl = (BoolLabel) data;

            if (bl.lTrue != "fall" && bl.lFalse != "fall"){
                m_writer.println("if " + node.getValue() + " == 1 goto " + bl.lTrue);
                m_writer.println("goto " + bl.lFalse);
            } else if (bl.lTrue != "fall" && bl.lFalse == "fall")
                m_writer.println("if " + node.getValue() + " == 1 goto " + bl.lTrue);
            else if (bl.lTrue == "fall" && bl.lFalse != "fall")
                m_writer.println("ifFalse " + node.getValue() + " == 1 goto " + bl.lFalse);
            else {
                // ERROR
            }
        }
        return node.getValue();
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }

    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        String testValue = (String) node.jjtGetChild(0).jjtAccept(this, data);
        String test = genLabel();

        m_writer.println("goto " + test);

        String[] labels = new String[node.jjtGetNumChildren()];
        String[] values = new String[node.jjtGetNumChildren()];

        for (int i = 1; i < node.jjtGetNumChildren() ; i++) {
            labels[i] = genLabel();
            values[i] = node.jjtGetChild(i).jjtAccept(this, labels[i]).toString();
            m_writer.println("goto " + data);
        }

        m_writer.println(test);

        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            if (node.jjtGetChild(i).toString() == "CaseStmt")
                m_writer.println("if " + testValue + " == " + values[i] + " goto " + labels[i]);
            else
                m_writer.println("goto " + labels[i]);
        }
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        m_writer.println((String) data);
        String caseValue = (String) node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);
        return caseValue;
    }

    @Override
    public Object visit(ASTDefaultStmt node, Object data) {
        m_writer.println((String) data);
        return node.childrenAccept(this, data);
    }

    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        Bool,
        Number
    }

    //utile surtout pour envoyé de l'informations au enfant des expressions logiques.
    private class BoolLabel {
        public String lTrue = null;
        public String lFalse = null;

        public BoolLabel(String t, String f) {
            lTrue = t;
            lFalse = f;
        }
    }

}