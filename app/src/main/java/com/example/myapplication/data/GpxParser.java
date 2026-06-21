package com.example.myapplication.data;

import android.util.Xml;

import com.example.myapplication.model.Trajectory;
import com.example.myapplication.model.TrajectoryPoint;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * GPX 格式的读写工具
 * GPX 1.1 规范: https://www.topografix.com/GPX/1/1/
 */
public class GpxParser {

    private static final String NS_GPX = "http://www.topografix.com/GPX/1/1";
    private static final String TAG_GPX = "gpx";
    private static final String TAG_METADATA = "metadata";
    private static final String TAG_NAME = "name";
    private static final String TAG_TIME = "time";
    private static final String TAG_TRK = "trk";
    private static final String TAG_TRKSEG = "trkseg";
    private static final String TAG_TRKPT = "trkpt";
    private static final String TAG_ELE = "ele";
    private static final String TAG_SPEED = "speed";
    private static final String ATTR_LAT = "lat";
    private static final String ATTR_LON = "lon";

    /** 将 Trajectory 写入 OutputStream（GPX 格式） */
    public static void write(Trajectory trajectory, OutputStream outputStream) throws Exception {
        XmlSerializer serializer = Xml.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

        serializer.startDocument("UTF-8", true);

        // <gpx>
        serializer.startTag(NS_GPX, TAG_GPX);
        serializer.attribute(null, "version", "1.1");
        serializer.attribute(null, "creator", "MyApplication");

        // <metadata>
        serializer.startTag(NS_GPX, TAG_METADATA);
        serializer.startTag(NS_GPX, TAG_NAME);
        serializer.text(trajectory.name);
        serializer.endTag(NS_GPX, TAG_NAME);
        serializer.startTag(NS_GPX, TAG_TIME);
        serializer.text(formatTime(trajectory.startTime));
        serializer.endTag(NS_GPX, TAG_TIME);
        serializer.endTag(NS_GPX, TAG_METADATA);

        // <trk>
        serializer.startTag(NS_GPX, TAG_TRK);
        serializer.startTag(NS_GPX, TAG_NAME);
        serializer.text(trajectory.name);
        serializer.endTag(NS_GPX, TAG_NAME);

        // <trkseg>
        serializer.startTag(NS_GPX, TAG_TRKSEG);

        for (TrajectoryPoint p : trajectory.points) {
            serializer.startTag(NS_GPX, TAG_TRKPT);
            serializer.attribute(null, ATTR_LAT, String.valueOf(p.latitude));
            serializer.attribute(null, ATTR_LON, String.valueOf(p.longitude));

            if (p.altitude != 0) {
                serializer.startTag(NS_GPX, TAG_ELE);
                serializer.text(String.valueOf(p.altitude));
                serializer.endTag(NS_GPX, TAG_ELE);
            }

            serializer.startTag(NS_GPX, TAG_TIME);
            serializer.text(formatTime(p.timestamp));
            serializer.endTag(NS_GPX, TAG_TIME);

            if (p.speed > 0) {
                serializer.startTag(NS_GPX, TAG_SPEED);
                serializer.text(String.valueOf(p.speed));
                serializer.endTag(NS_GPX, TAG_SPEED);
            }

            serializer.endTag(NS_GPX, TAG_TRKPT);
        }

        serializer.endTag(NS_GPX, TAG_TRKSEG);
        serializer.endTag(NS_GPX, TAG_TRK);
        serializer.endTag(NS_GPX, TAG_GPX);

        serializer.endDocument();

        outputStream.write(writer.toString().getBytes("UTF-8"));
    }

    /** 从 InputStream 读取 GPX 并解析为 Trajectory */
    public static Trajectory read(InputStream inputStream) throws Exception {
        Trajectory trajectory = new Trajectory();

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(inputStream, "UTF-8");

        String currentTag = null;
        TrajectoryPoint currentPoint = null;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();

            switch (eventType) {
                case XmlPullParser.START_TAG:
                    currentTag = tagName;
                    switch (tagName) {
                        case TAG_TRKPT:
                            currentPoint = new TrajectoryPoint();
                            currentPoint.latitude = Double.parseDouble(
                                    parser.getAttributeValue(null, ATTR_LAT));
                            currentPoint.longitude = Double.parseDouble(
                                    parser.getAttributeValue(null, ATTR_LON));
                            break;
                        case TAG_NAME:
                            // metadata > name 或 trk > name
                            break;
                    }
                    break;

                case XmlPullParser.TEXT:
                    if (currentPoint != null && currentTag != null) {
                        String text = parser.getText().trim();
                        if (text.isEmpty()) break;
                        switch (currentTag) {
                            case TAG_ELE:
                                currentPoint.altitude = Double.parseDouble(text);
                                break;
                            case TAG_TIME:
                                currentPoint.timestamp = parseTime(text);
                                break;
                            case TAG_SPEED:
                                currentPoint.speed = Float.parseFloat(text);
                                break;
                        }
                    } else if (currentTag != null) {
                        String text = parser.getText().trim();
                        if (text.isEmpty()) break;
                        if (TAG_NAME.equals(currentTag)) {
                            // 优先用 trk > name，其次 metadata > name
                            if (trajectory.name == null || trajectory.name.isEmpty()) {
                                trajectory.name = text;
                            }
                        } else if (TAG_TIME.equals(currentTag) && trajectory.startTime == 0) {
                            trajectory.startTime = parseTime(text);
                        }
                    }
                    break;

                case XmlPullParser.END_TAG:
                    if (TAG_TRKPT.equals(tagName) && currentPoint != null) {
                        trajectory.points.add(currentPoint);
                        currentPoint = null;
                    }
                    currentTag = null;
                    break;
            }

            eventType = parser.next();
        }

        // 设置 endTime 为最后一个点的时间
        if (!trajectory.points.isEmpty()) {
            trajectory.endTime = trajectory.points.get(trajectory.points.size() - 1).timestamp;
        }

        return trajectory;
    }

    // ============ 时间格式转换 ============

    /** 毫秒时间戳 → ISO 8601 (如 2026-06-15T14:30:01Z) */
    private static String formatTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(millis));
    }

    /** ISO 8601 → 毫秒时间戳 */
    private static long parseTime(String isoTime) throws Exception {
        String normalized = isoTime;
        if (normalized.contains(".")) {
            normalized = normalized.replaceAll("\\.\\d+", "");
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.parse(normalized).getTime();
    }
}
