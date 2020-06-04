package handlingdep;

import configinterface.ConfigList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import utility.OutputFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class DefaultDep {
    private Boolean contain(Element ele, String tagName){
        return ele.getElementsByTagName(tagName).getLength()>0;
    }

    public HashMap<String,String> handle(String path) throws IOException, SAXException {
        File f = new File(path);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        Document doc = dBuilder.parse(f);
        NodeList nList = doc.getElementsByTagName("property");

        HashMap<String,String> result = new HashMap<>();

        for(int i=0;i<nList.getLength();++i){
            Node node = nList.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE){
                Element eElement = (Element) node;

                String name = eElement.getElementsByTagName("name").item(0).getTextContent();
                name = name.replaceAll("\\s+",""); // alluxio has empty space in config files

                if(contain(eElement,"value")) {
                    result.put(name,eElement.getElementsByTagName("value").item(0).getTextContent());
                }else{
                    result.put(name,"");
                }
            }
        }

        return result;
    }

    public static void analyzeConfigFile(String  [] fileNames) throws IOException, SAXException {
        /* identifying pattern one of default value config dependency */
        new ConfigList();
        DefaultDep defaultdeps =  new DefaultDep();
        LinkedList<Map<String,String>> results =  new LinkedList<>();
        for(int i=0;i<fileNames.length;++i){
            results.add(defaultdeps.handle(fileNames[i]));
        }
        for(int i=0;i<results.size();++i){
            for(String one: results.get(i).keySet()) {
                for (int j = i; j < results.size(); ++j) {
                    for(String two: results.get(j).keySet()){
                        if(one.compareTo(two)!=0){
                            if(results.get(i).get(one).contains(two) || results.get(j).get(two).contains(one)){
                                OutputFormat.output(one,two);
                            }
                        }
                    }
                }
            }
        }
    }
}
