import javafx.scene.control.TreeItem;

import java.util.List;
import java.util.Map;

public class MappingsClass extends MappingsOtherClass {
    public String obfName;

    public Map<String, MappingsMethod> methods;
    public Map<String, MappingsField> fields;

    public List<TreeItem> uiReferences;
}
