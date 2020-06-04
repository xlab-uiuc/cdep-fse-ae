package configinterface;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

public class ConfigList {
    public static HashSet<String>allConfigs =  new HashSet<>();
    String [] configFileNames={"yarn-default.xml","zookeeper-default.xml","alluxio-default.xml","core-default.xml","hbase-default.xml","hdfs-default.xml","mapred-default.xml"};;
    public ConfigList() throws IOException, SAXException {
        for(int i=0;i<configFileNames.length;++i) {
            File f = new File("./config_files/"+configFileNames[i]);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = null;
            try {
                dBuilder = dbFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            }
            Document doc = dBuilder.parse(f);
            NodeList nList = doc.getElementsByTagName("property");


            for (int ii = 0; ii < nList.getLength(); ++ii) {
                Node node = nList.item(ii);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) node;

                    String name = eElement.getElementsByTagName("name").item(0).getTextContent();
                    if(!allConfigs.contains(name)){
                        allConfigs.add(name);
                    }
                }
            }
        }
    }
    public static boolean isConfig(String name){
        return allConfigs.contains(name);
    }
}
