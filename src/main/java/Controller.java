import com.google.gson.*;
import edu.umd.cs.findbugs.ba.SignatureParser;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import sun.reflect.generics.tree.*;
import sun.reflect.generics.visitor.TypeTreeVisitor;

import java.io.*;
import java.util.*;

public class Controller {
    public TreeView oldMappingsTree;
    public TreeView newMappingsTree;

    public Mappings oldMappings;
    public Mappings newMappings;

    public void initialize() {
    }

    public void openOldMappings(ActionEvent actionEvent) {
        FileChooser chooser = new FileChooser();
        File file = chooser.showOpenDialog(Main.stage);

        if (file != null) {
            try {
                oldMappings = parseMappings(new Gson().fromJson(new FileReader(file), JsonElement.class));
                buildTree(oldMappings, oldMappingsTree, false);

                newMappings = parseMappings(new Gson().fromJson(new FileReader(file), JsonElement.class));
                buildTree(newMappings, newMappingsTree, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void buildTree(Mappings mappings, final TreeView tree, boolean editable) {
        tree.setRoot(new TreeItem("Classes"));
        tree.getRoot().setExpanded(true);

        final HashMap<TreeItem, MappingsClass> classesForItems = new HashMap<TreeItem, MappingsClass>();
        final HashMap<TreeItem, MappingsMethod> methodsForItems = new HashMap<TreeItem, MappingsMethod>();
        final HashMap<TreeItem, MappingsField> fieldsForItems = new HashMap<TreeItem, MappingsField>();
        if (editable) {
            tree.setEditable(true);
            tree.setCellFactory(new Callback<TreeView, TreeCell>() {
                @Override
                public TreeCell call(TreeView param) {
                    final TreeCell cell = new TextFieldTreeCellImpl() {
                        @Override
                        public void startEdit() {
                            if (classesForItems.containsKey(getTreeItem())) {
                                super.startEdit();
                                ((TextField) getGraphic()).setText(classesForItems.get(getTreeItem()).obfName);
                            }

                            if (methodsForItems.containsKey(getTreeItem())) {
                                super.startEdit();
                                ((TextField) getGraphic()).setText(methodsForItems.get(getTreeItem()).obfName);
                            }

                            if (fieldsForItems.containsKey(getTreeItem())) {
                                super.startEdit();
                                ((TextField) getGraphic()).setText(fieldsForItems.get(getTreeItem()).obfName);
                            }
                        }

                        @Override
                        public void commitEdit(String newValue) {
                            super.commitEdit(newValue);

                            if (classesForItems.containsKey(getTreeItem())) {
                                classesForItems.get(getTreeItem()).obfName = newValue;

                                updateClassInUI(classesForItems.get(getTreeItem()));
                            }

                            if (methodsForItems.containsKey(getTreeItem())) {
                                methodsForItems.get(getTreeItem()).obfName = newValue;

                                updateMethodInUI(methodsForItems.get(getTreeItem()));
                            }

                            if (fieldsForItems.containsKey(getTreeItem())) {
                                fieldsForItems.get(getTreeItem()).obfName = newValue;

                                updateFieldInUI(fieldsForItems.get(getTreeItem()));
                            }
                        }
                    };

                    cell.addEventHandler(MouseEvent.ANY, new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent event) {
                            if (classesForItems.containsKey(cell.getTreeItem())) {
                                if (event.getClickCount() == 2) {
                                    cell.startEdit();
                                }
                            }
                        }
                    });

                    return cell;
                }
            });
        }


        ArrayList<Map.Entry<String, MappingsClass>> entries = new ArrayList(mappings.classes.entrySet());

        Collections.sort(entries, new Comparator<Map.Entry<String, MappingsClass>>() {
            @Override
            public int compare(Map.Entry<String, MappingsClass> o1, Map.Entry<String, MappingsClass> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });

        for (final Map.Entry<String, MappingsClass> clazz : entries) {
            final TreeItem classItem = new TreeItem(clazz.getKey() + " (" + clazz.getValue().obfName + ")");
            tree.getRoot().getChildren().add(classItem);

            if (editable) {
                clazz.getValue().uiReferences.add(classItem);
                classesForItems.put(classItem, clazz.getValue());
            }

            if (!clazz.getValue().methods.isEmpty()) {
                TreeItem methodsItem = new TreeItem("Methods");
                classItem.getChildren().add(methodsItem);

                for (Map.Entry<String, MappingsMethod> method : clazz.getValue().methods.entrySet()) {
                    TreeItem methodItem = new TreeItem(method.getValue().name + " (" + method.getValue().obfName + ")");

                    methodsItem.getChildren().add(methodItem);

                    if (editable) {
                        method.getValue().uiReferences.add(methodItem);
                        methodsForItems.put(methodItem, method.getValue());
                    }

                    if (!method.getValue().parameters.isEmpty()) {
                        TreeItem paramsItem = new TreeItem("Parameters");
                        methodItem.getChildren().add(paramsItem);

                        for (MappingsOtherClass param : method.getValue().parameters) {
                            TreeItem paramItem;

                            if (param instanceof MappingsClass) {
                                paramItem = new TreeItem(param.name + " (" + ((MappingsClass) param).obfName + ")");

                                if (editable) {
                                    classesForItems.put(paramItem, (MappingsClass) param);
                                }

                                ((MappingsClass) param).uiReferences.add(paramItem);
                            } else {
                                paramItem = new TreeItem(param.name);
                            }

                            paramsItem.getChildren().add(paramItem);
                        }
                    }
                }
            }

            if (!clazz.getValue().fields.isEmpty()) {
                TreeItem fieldsItem = new TreeItem("Fields");
                classItem.getChildren().add(fieldsItem);

                for (Map.Entry<String, MappingsField> field : clazz.getValue().fields.entrySet()) {
                    TreeItem fieldItem = new TreeItem(field.getValue().name + " (" + field.getValue().obfName + ")");
                    fieldsItem.getChildren().add(fieldItem);

                    if (editable) {
                        fieldsForItems.put(fieldItem, field.getValue());
                    }

                    field.getValue().uiReferences.add(fieldItem);
                }
            }
        }
    }

    public void updateClassInUI(MappingsClass updatedClass) {
        for (TreeItem item : updatedClass.uiReferences) {
            item.setValue(updatedClass.name + " (" + updatedClass.obfName + ")");
        }
    }

    public void updateMethodInUI(MappingsMethod updatedMethod) {
        for (TreeItem item : updatedMethod.uiReferences) {
            item.setValue(updatedMethod.name + " (" + updatedMethod.obfName + ")");
        }
    }

    public void updateFieldInUI(MappingsField updatedField) {
        for (TreeItem item : updatedField.uiReferences) {
            item.setValue(updatedField.name + " (" + updatedField.obfName + ")");
        }
    }

    public void openNewMappings(ActionEvent actionEvent) {
        FileChooser chooser = new FileChooser();
        File file = chooser.showOpenDialog(Main.stage);

        if (file != null) {
            try {
                newMappings = parseMappings(new Gson().fromJson(new FileReader(file), JsonElement.class));
                buildTree(newMappings, newMappingsTree, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public Mappings parseMappings(JsonElement elem) {
        Mappings mappings = new Mappings();
        mappings.classes = new HashMap<String, MappingsClass>();
        mappings.obfClasses = new HashMap<String, MappingsClass>();

        JsonObject classesObj = elem.getAsJsonObject().getAsJsonObject("classes");

        for (Map.Entry<String, JsonElement> classElem : classesObj.entrySet()) {
            MappingsClass clazz = new MappingsClass();

            clazz.name = classElem.getKey();
            clazz.obfName = classElem.getValue().getAsJsonObject().getAsJsonPrimitive("name").getAsString();
            clazz.uiReferences = new LinkedList<TreeItem>();

            mappings.classes.put(clazz.name, clazz);
            mappings.obfClasses.put(clazz.obfName, clazz);
        }

        for (Map.Entry<String, JsonElement> classElem : classesObj.entrySet()) {
            MappingsOtherClass otherClazz = getClass(mappings, classElem.getValue().getAsJsonObject().getAsJsonPrimitive("name").getAsString());

            if (otherClazz instanceof MappingsClass) {
                MappingsClass clazz = (MappingsClass) otherClazz;
                Map<String, MappingsMethod> methods = new HashMap<String, MappingsMethod>();
                Map<String, MappingsField> fields = new HashMap<String, MappingsField>();

                if (classElem.getValue().getAsJsonObject().has("methods")) {
                    for (Map.Entry<String, JsonElement> methodElem : classElem.getValue().getAsJsonObject().getAsJsonObject("methods").entrySet()) {
                        MappingsMethod method = new MappingsMethod();
                        method.uiReferences = new ArrayList<TreeItem>();
                        method.name = methodElem.getValue().getAsString();

                        String sig = methodElem.getKey();
                        method.obfName = sig.split("\\(")[0];

                        String params = "(" + sig.split("\\(")[1];

                        new SignatureParser(params).getArguments();

                        final List<MappingsOtherClass> paramss = new ArrayList<MappingsOtherClass>();

                        for (String argument : new SignatureParser(params).getArguments()) {
                            paramss.add(getClass(mappings, getTypeName(sun.reflect.generics.parser.SignatureParser.make().parseTypeSig(argument))));
                        }
                        method.parameters = paramss;

                        String returnn = new SignatureParser(params).getReturnTypeSignature();
                        if (returnn.equals("V")) {
                            method.returnType = getClass(mappings, "void");
                        } else {
                            method.returnType = getClass(mappings, getTypeName(sun.reflect.generics.parser.SignatureParser.make().parseTypeSig(returnn)));
                        }

                        methods.put(method.name, method);
                    }
                }

                if (classElem.getValue().getAsJsonObject().has("fields")) {
                    for (Map.Entry<String, JsonElement> fieldElem : classElem.getValue().getAsJsonObject().getAsJsonObject("fields").entrySet()) {
                        MappingsField field = new MappingsField();

                        field.name = fieldElem.getValue().getAsString();
                        field.obfName = fieldElem.getKey();
                        field.uiReferences = new ArrayList<TreeItem>();

                        fields.put(field.name, field);
                    }
                }

                clazz.methods = methods;
                clazz.fields = fields;
            }
        }
        return mappings;
    }

    public MappingsOtherClass getClass(Mappings mappings, String name) {
        MappingsOtherClass clazz;
        if (mappings.obfClasses.containsKey(name)) {
            clazz = mappings.obfClasses.get(name);
        } else {
            clazz = new MappingsOtherClass();
            clazz.name = name;
        }
        return clazz;
    }

    public String getTypeName(final ReturnType type) {
        final String[] val = {""};
        type.accept(new TypeTreeVisitor<Object>() {
            @Override
            public Object getResult() {
                return null;
            }

            @Override
            public void visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {

            }

            @Override
            public void visitClassTypeSignature(ClassTypeSignature classTypeSignature) {
                val[0] = classTypeSignature.getPath().get(classTypeSignature.getPath().size() - 1).getName();
            }

            @Override
            public void visitArrayTypeSignature(ArrayTypeSignature arrayTypeSignature) {
                val[0] = getTypeName(arrayTypeSignature.getComponentType()) + "[]";
            }

            @Override
            public void visitTypeVariableSignature(TypeVariableSignature typeVariableSignature) {
            }

            @Override
            public void visitWildcard(Wildcard wildcard) {

            }

            @Override
            public void visitSimpleClassTypeSignature(SimpleClassTypeSignature simpleClassTypeSignature) {

            }

            @Override
            public void visitBottomSignature(BottomSignature bottomSignature) {

            }

            @Override
            public void visitByteSignature(ByteSignature byteSignature) {
                val[0] = "byte";
            }

            @Override
            public void visitBooleanSignature(BooleanSignature booleanSignature) {
                val[0] = "boolean";
            }

            @Override
            public void visitShortSignature(ShortSignature shortSignature) {
                val[0] = "short";
            }

            @Override
            public void visitCharSignature(CharSignature charSignature) {
                val[0] = "char";
            }

            @Override
            public void visitIntSignature(IntSignature intSignature) {
                val[0] = "int";
            }

            @Override
            public void visitLongSignature(LongSignature longSignature) {
                val[0] = "long";
            }

            @Override
            public void visitFloatSignature(FloatSignature floatSignature) {
                val[0] = "float";
            }

            @Override
            public void visitDoubleSignature(DoubleSignature doubleSignature) {
                val[0] = "double";
            }

            @Override
            public void visitVoidDescriptor(VoidDescriptor voidDescriptor) {
                val[0] = "void";
            }
        });
        return val[0];
    }

    public void save(ActionEvent actionEvent) {
        JsonObject root = new JsonObject();

        JsonObject classes = new JsonObject();
        root.add("classes", classes);

        for (MappingsClass clazz : newMappings.classes.values()) {
            JsonObject clazzObj = new JsonObject();
            classes.add(clazz.name, clazzObj);

            clazzObj.addProperty("name", clazz.obfName);

            JsonObject methods = new JsonObject();
            clazzObj.add("methods", methods);

            for (MappingsMethod method : clazz.methods.values()) {
                String sig = method.obfName;

                sig += "(";
                for (MappingsOtherClass clazzz : method.parameters) {
                    sig += toJvmType(clazzz instanceof MappingsClass ? ((MappingsClass) clazzz).obfName : clazzz.name);
                }
                sig += ")";
                sig += toJvmType(method.returnType instanceof MappingsClass ? ((MappingsClass) method.returnType).obfName : method.returnType.name);

                methods.addProperty(sig, method.name);
            }

            JsonObject fields = new JsonObject();
            clazzObj.add("fields", fields);

            for (MappingsField field : clazz.fields.values()) {
                fields.addProperty(field.obfName, field.name);
            }
        }

        FileChooser chooser = new FileChooser();
        File f = chooser.showSaveDialog(Main.stage);

        if (f != null) {
            try {
                FileWriter ff = new FileWriter(f);
                new GsonBuilder().setPrettyPrinting().create().toJson(root, ff);
                ff.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String toJvmType(String type) {
        if (type.endsWith("[]")) {
            return "[" + toJvmType(type.substring(0, type.length() - 2));
        }

        if (type.equals("long")) return "J";
        if (type.equals("double")) return "D";
        if (type.equals("int")) return "I";
        if (type.equals("float")) return "F";
        if (type.equals("char")) return "C";
        if (type.equals("short")) return "S";
        if (type.equals("byte")) return "B";
        if (type.equals("boolean")) return "Z";
        if (type.equals("void")) return "V";
        return "L" + type.replaceAll("\\.", "/") + ";";
    }

    private class TextFieldTreeCellImpl extends TreeCell<String> {

        private TextField textField;

        public TextFieldTreeCellImpl() {
        }

        @Override
        public void startEdit() {
            super.startEdit();

            if (textField == null) {
                createTextField();
            }
            setText(null);
            setGraphic(textField);
            textField.selectAll();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(getTreeItem().getGraphic());
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getString());
                    setGraphic(getTreeItem().getGraphic());
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getString());
            textField.setOnKeyReleased(new EventHandler<KeyEvent>() {

                @Override
                public void handle(KeyEvent t) {
                    if (t.getCode() == KeyCode.ENTER) {
                        commitEdit(textField.getText());
                    } else if (t.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                    }
                }
            });
        }

        private String getString() {
            return getItem() == null ? "" : getItem().toString();
        }
    }
}