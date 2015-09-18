package services;

import com.mongodb.DB;
import com.mongodb.DBObject;
import dao.LevelDAO;
import dao.PathDAO;
import entity.SankeyGraph;
import entity.StreamEdge;
import entity.URLNode;

import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathService {

    PathDAO pathDAO;
    LevelDAO levelDAO;

    public PathService(DB db) {
        pathDAO = new PathDAO(db);
        levelDAO = new LevelDAO(db);  //��Ҫ�õ�regex,name
    }

    public SankeyGraph getGraph(int depth, String startTime, String endTime) throws UnknownHostException, ParseException {

        List<URLNode> nodes;
        //��session��ǵ�·�����ݣ����ڼ���ÿ���ڵ��out_degree��in_degree
        List<StreamEdge> links;
        //����session��ǵ�·�����ݣ���ÿ���ߵ�Ȩֵ��������ʾ
        List<StreamEdge> noSessionLinksList;

        SankeyGraph graph = null;
        int node_index = 0;   //������name

        nodes = new ArrayList<URLNode>();
        links = new ArrayList<StreamEdge>();
        noSessionLinksList = new ArrayList<StreamEdge>();

        //����map���ڴ�ţ����->index�������յ�->index����ӳ��
        Map<String, Integer> startMap = null;
        Map<String, Integer> endMap = null;

        //depthmap���ڴ��ÿ���ڵ㣨��name��ʶ������·������ȵĶ�Ӧ��ϵ
        Map<Integer, Integer> depthMap = new HashMap<Integer, Integer>();

        //����� ȡ ������
        for (int i = 0; i < depth; i++) {
            //��Ų�ͬsession��ͬ�����յ��·�ε�ln(count)֮��
            Map<String, Double> segCount = new HashMap<String, Double>();
            //��ʼ��map
            if (i == 0) {
                startMap = new HashMap<String, Integer>();
            } else {
                startMap = endMap;
            }
            endMap = new HashMap<String, Integer>();
            //һ��ȡ����
            List<DBObject> pathGroupList = pathDAO.groupByFour(i + 1, i + 2, startTime, endTime);

            //ȡ�õ����ݰ��� _id(start,end,session),nums
            for (DBObject obj : pathGroupList) {
                int count = (Integer) obj.get("nums");  //��ñߵ�count
                DBObject idObject = (DBObject) obj.get("_id");
                String start_url = (String) idObject.get("P" + (i + 1));
                String end_url = (String) idObject.get("P" + (i + 2));
                String session = (String) idObject.get("session");

                if (!(start_url.equals("null")) && !(end_url.equals("null"))) {
                    if (i == 0) {
                        //startMap��<start_url,node_index>
                        if (!startMap.containsKey(start_url)) {
                            startMap.put(start_url, node_index);
                            depthMap.put(node_index, i);
                            node_index++;
                        }
                    }
                    //endMap��<end_url,node_index>
                    if (!endMap.containsKey(end_url)) {
                        endMap.put(end_url, node_index);
                        depthMap.put(node_index, i + 1);
                        node_index++;
                    }

                    StreamEdge link = new StreamEdge(startMap.get(start_url), endMap.get(end_url), (double) count);
                    link.setSession(session);
                    links.add(link);

                    String key = start_url + "~" + end_url;
                    // System.out.println(key);
                    if (!segCount.containsKey(key)) {
                        segCount.put(key, Math.log(count) + 1);
                    } else {
                        double old_count = segCount.get(key);
                        segCount.put(key, old_count + Math.log(count) + 1);
                    }

                }
            }
            //
            for (Map.Entry<String, Double> entry : segCount.entrySet()) {
                String key = entry.getKey();
                double count = entry.getValue();
                String start_url = key.split("~")[0];
                String end_url = key.split("~")[1];

                StreamEdge noSessionLinks = new StreamEdge(startMap.get(start_url), endMap.get(end_url), count);

                noSessionLinksList.add(noSessionLinks);
            }

            if (i == 0) {
                for (Map.Entry<String, Integer> entry : startMap.entrySet()) {
                    //startMap<start_url, node_index>
                    //node_index  >>>>  name
                    //URLNode(Integer name,String url)
                    URLNode node = new URLNode(entry.getValue(), entry.getKey());
                    node.setDepth(depthMap.get(entry.getValue()));
                    nodes.add(node);
                }
            }
            for (Map.Entry<String, Integer> entry : endMap.entrySet()) {
                URLNode node = new URLNode(entry.getValue(), entry.getKey());
                node.setDepth(depthMap.get(entry.getValue()));
                nodes.add(node);
            }

        }
        //��nodes����name����
        for (URLNode node : nodes) {
            //����map���ڼ�¼ͬһ��session���Ե�ǰ�ڵ��Ϊ���ıߵ�value֮��
            Map<String, Double> in_session = new HashMap<String, Double>();
            Map<String, Double> out_session = new HashMap<String, Double>();
            //����ÿ��node�ĳ��Ⱥ����
            double out = 0;
            double in = 0;
            for (StreamEdge link : links) {
                if (link.getSource() == node.getName()) {
                    if (out_session.containsKey(link.getSession())) {
                        double value = out_session.get(link.getSession());
                        out_session.put(link.getSession(), value + link.getValue());
                    } else
                        out_session.put(link.getSession(), link.getValue());
                }
                if (link.getTarget() == node.getName()) {
                    if (in_session.containsKey(link.getSession())) {
                        double value = in_session.get(link.getSession());
                        in_session.put(link.getSession(), value + link.getValue());
                    } else
                        in_session.put(link.getSession(), link.getValue());
                }
            }
            for (Map.Entry<String, Double> entry : out_session.entrySet()) {
                out += Math.log(entry.getValue()) + 1;
            }
            for (Map.Entry<String, Double> entry : in_session.entrySet()) {
                in += Math.log(entry.getValue()) + 1;
            }
            node.setIn_degree(in);
            node.setOut_degree(out);

            //����ÿ��node��url��������Ϣ
            String[][] RegexName = levelDAO.getRegexName();
            for (int j = 0; j < RegexName.length; j++) {
                Matcher m = Pattern.compile(RegexName[j][0]).matcher(node.getUrl());
                while (m.find()) {
                    node.setSemantics(RegexName[j][1]);  //
                }
            }
        }
        //depth�������Ժ�
        //����depthΪ0�Ľڵ�����
        Map<String, Double> Startout_session = new HashMap<String, Double>();
        List<DBObject> path0List = pathDAO.getDepth0(startTime, endTime);
        //ȡ�õ����ݰ��� _id(start,session),nums
        for (DBObject obj : path0List) {
            int count = (Integer) obj.get("nums");
            DBObject idObject = (DBObject) obj.get("_id");
            String start_url = (String) idObject.get("P1");
            //  String session = (String) idObject.get("session");

            if (Startout_session.containsKey(start_url)) {
                double value = Startout_session.get(start_url);
                Startout_session.put(start_url, value + Math.log((double) count) + 1);
            } else
                Startout_session.put(start_url, Math.log((double) count) + 1);
        }
        //����start_url�����
        for (URLNode node : nodes) {
            if (node.getDepth() == 0) {
                node.setIn_degree(Startout_session.get(node.getUrl()));
            }
        }
        //����ÿ���ڵ��������
        for (URLNode node : nodes) {
            double in = node.getIn_degree();
            double out = node.getOut_degree();
            node.setDrop_per((in - out) / in);
        }

        graph = new SankeyGraph(nodes, noSessionLinksList);
        return graph;
    }
}
