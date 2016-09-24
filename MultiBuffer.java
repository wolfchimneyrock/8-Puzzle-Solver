
/////////////////////////////////////////////////////////////////////////////////////
// Robert Wagner
// CISC 3410 Assignment #1
// 2016-09-11
// MultiBuffer.java 
// print multi-line strings side-by-side
////////////////////////////////////////////////////////////////////////////////////
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;

public class MultiBuffer  {
    public MultiBuffer(int maxWidth) {
        buffer = new ArrayList<String>();
        widths = new ArrayList<Integer>();
        widthLimit = maxWidth;
        h = 0;
        w = 0;
        cursorLine = 0;
    }
    public MultiBuffer() { this(80); }

    public void add(String str) {
            // to calculate an accurate width, we need to strip any ANSI
            // control codes, and keep duplicate copy with the codes
        String stripped = str.replaceAll("\u001B\\[[\\d;]*[^\\d;]","");
        String[] lines = str.split("\n");
        String[] slines = stripped.split("\n");
        int maxWidth = 0;
        for (int i = 0; i < lines.length; i++) 
            if (slines[i].length() > maxWidth) maxWidth = slines[i].length();
        boolean fit = false;
        while (!fit) { 
            while (cursorLine + lines.length + 1 > buffer.size()) {
                buffer.add("");
                widths.add(0);
                h = cursorLine;
            }
            fit = true;
            for (int i = cursorLine; i < cursorLine + lines.length; i++)
                if (buffer.get(i) != null && 
                    widths.get(i) + slines[i-cursorLine].length() > widthLimit) 
                    fit = false;
            if (!fit) cursorLine = cursorLine + slines.length + 1;
        }
        this.addAtLine(cursorLine, lines, slines);       
    }

    private void addAtLine(int lineNo, String[] lines, String[] slines) {
        // see if we need to append new lines and do it
        int heightReq = lineNo + lines.length;
        if (heightReq > buffer.size()) {
            buffer.addAll(Collections.nCopies(heightReq - h, " "));
            widths.addAll(Collections.nCopies(heightReq - h, 0));
        }
            //for (int i = 0; i < heightReq - h; i++) buffer.add("");
        h = buffer.size();
        // find max width at existing lines effected
        int maxExWidth = 0;
        for (int i = lineNo; i < lineNo + lines.length; i++)
            if (maxExWidth < widths.get(i))  maxExWidth = widths.get(i);
        // add required padding and the new string
        for (int i = lineNo; i < lineNo + lines.length; i++) {
            int padding = maxExWidth - widths.get(i);
            //String old = buffer.get(i);
            buffer.set(i, buffer.get(i) +  
                                      repeat(' ',padding) +
                                      lines[i - lineNo]
                      );
            widths.set(i, widths.get(i) + padding + slines[i-lineNo].length());
            if (widths.get(i) > w) w = widths.get(i);
        }
        
    }
    private String repeat(char c, int n) {
        String adder = Character.toString(c);
        String result = "";
        while (n > 0) {
            if (n % 2 == 1) {
                result += adder;
            }
            adder += adder;
            n /= 2;
            }        
        return result;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (String line: buffer) {
            sb.append(line);
            sb.append('\n');
        }
        return sb.toString();
    }

    public int width()  { return w; }
    public int height() { return h; }

    private ArrayList<String>  buffer;
    private ArrayList<Integer> widths;
    private int h;
    private int w;
    private int widthLimit;
    private int cursorLine;
}

