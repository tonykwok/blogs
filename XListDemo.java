/**
 * Refer to https://today.java.net/pub/a/today/2007/09/27/fling-scroller.html for details
 */
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

import org.jdesktop.animation.timing.Animator;
import org.jdesktop.animation.timing.TimingTargetAdapter;
import org.jdesktop.animation.timing.interpolation.PropertySetter;
import org.jdesktop.animation.timing.interpolation.SplineInterpolator;

public class XListDemo {
    
    private static final String[] ITEMS = new String[] {"200","AbstractAction","ActionEvent","BorderLayout","CLOSE","Click","Dimension","EXIT","ITEMS","JButton","JFrame","JList","JXPanel","ListModel","ON","SOUTH","String","XList","actionPerformed","add","args","awt","b","class","e","event","extends","f","final","import","java","javax","jdesktop","l","main","me","new","org","pack","param","private","public","setDefaultCloseOperation","setPreferredSize","setVisible","static","swing","swingx","true","void"};

    private static final String[][] ITEMS_T = new String[][] {{"200","AbstractAction"},{"ActionEvent","BorderLayout"},{"CLOSE","Click"},{"Dimension","EXIT"},{"ITEMS","JButton"},{"JFrame","JList"},{"JXPanel","ListModel"},{"ON","SOUTH"},{"String","XList"},{"actionPerformed","add"},{"args","awt"},{"b","class"},{"e","event"},{"extends","f"},{"final","import"},{"java","javax"},{"jdesktop","l"},{"main","me"},{"new","org"},{"pack","param"},{"private","public"},{"setDefaultCloseOperation","setPreferredSize"},{"setVisible","static"},{"swing","swingx"},{"true","void"}};

    public static void main(String[] args) {
        // JList example
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JList list = new JList(ITEMS);
        decorate(list);
        f.add(new JScrollPane(list));
        f.setPreferredSize(new Dimension(200, 200));
        f.pack();
        f.setVisible(true);

        // JTable example
        f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTable t = new JTable(ITEMS_T,new String[] {"col1", "col2"});
        decorate(t);
        f.add(new JScrollPane(t));
        f.setPreferredSize(new Dimension(200, 200));
        f.pack();
        f.setVisible(true);
}

    /**
     * Adds scroll gesture support to the list.
     * @param list JList to be decorated.
     */
    private static void decorate(JList list) {
        final StateControl state = new StateControl();
        list.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                // store the start possition
                Point p = e.getComponent().getLocationOnScreen();
                state.startY = e.getY() + p.y;
                state.startIdx = ((JList) e.getSource()).getSelectedIndex();
                state.startTime = System.currentTimeMillis();
            }

            public void mouseReleased(MouseEvent e) {
                // store the end position
                Point p = e.getComponent().getLocationOnScreen();
                state.stopY = e.getY() + p.y;
                state.stopIdx = ((JList) e.getSource()).getSelectedIndex();
                state.stopTime = System.currentTimeMillis();

                state.startMotion((JList)e.getSource());
            }});
    }

    /**
     * Adds scroll gesture support to the list.
     * @param list JList to be decorated.
     */
    private static void decorate(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final StateControl state = new StateControl();
        table.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                // store the start possition
                Point p = e.getComponent().getLocationOnScreen();
                state.startY = e.getY() + p.y;
                state.startIdx = ((JTable) e.getSource()).getSelectedRow();
                state.startTime = System.currentTimeMillis();
            }

            public void mouseReleased(MouseEvent e) {
                // store the end position
                Point p = e.getComponent().getLocationOnScreen();
                state.stopY = e.getY() + p.y;
                state.stopIdx = ((JTable) e.getSource()).getSelectedRow();
                state.stopTime = System.currentTimeMillis();

                state.startMotion((JTable)e.getSource());
            }});
    }

    /**
     * <code>StateControl</code> is a state holder for the scroll gesture. 
     */
    static class StateControl {
        private static final int TAIL = 10;
        protected long stopTime;
        protected long startTime;
        int startY;
        int startIdx;
        int stopY;
        int stopIdx;
        private Animator a;

        public void startMotion(final JList list) {
            if (a != null && a.isRunning()) {
                a.stop();
            }
            // bail out if there was no mouse movement in between
            if (startY == stopY) {
                return;
            }
            // calculate time and distacne for scrolling
            int dist = Math.abs(startIdx - stopIdx);
            // bail out if there was change in item selection
            if (dist == 0) {
                return;
            }
            int tail = Math.min(TAIL, dist);
            int stopInt = startY < stopY? Math.min(list.getSelectedIndex() + tail, list.getModel().getSize()) : Math.max(list.getSelectedIndex() - tail, 0);
            // create property setter to change selected list item
            PropertySetter ps = new PropertySetter(list, "selectedIndex", list.getSelectedIndex() , stopInt);
            a = new Animator((int)((stopTime-startTime)*tail/dist), ps);
            // attach timing target to ensure selected item stays visible.
            a.addTarget(new TimingTargetAdapter() {
                public void timingEvent(float fraction) {
                    list.scrollRectToVisible(
                    list.getCellBounds(list.getSelectedIndex(), list.getSelectedIndex()));
                }});
            // set interpolater to mimic slowdown at the end of scrolling
            a.setInterpolator(new SplineInterpolator(0f,.02f,0f,1f));
            // finally start the timer
            a.start();
        }

        public void startMotion(final JTable table) {
            if (a != null && a.isRunning()) {
                a.stop();
            }
            // bail out if there was no mouse movement in between
            if (startY == stopY) {
                return;
            }
            // calculate time and distacne for scrolling
            int dist = Math.abs(startIdx - stopIdx);
            // bail out if there was change in item selection
            if (dist == 0) {
                return;
            }
            int tail = Math.min(TAIL, dist);
            int stopInt = startY < stopY? Math.min(table.getSelectedRow() + tail, table.getRowCount()) : Math.max(table.getSelectedRow() - tail, 0);
            // create property setter to change selected list item
            PropertySetter ps = new PropertySetter(table.getSelectionModel(), "leadSelectionIndex", table.getSelectedRow() , stopInt);
            a = new Animator((int)((stopTime-startTime)*tail/dist), ps);
            // attach timing target to ensure selected item stays visible.
            a.addTarget(new TimingTargetAdapter() {
                public void timingEvent(float fraction) {
                    table.scrollRectToVisible(
                    table.getCellRect(table.getSelectionModel().getLeadSelectionIndex(), -1, true));
                }});
            // set interpolater to mimic slowdown at the end of scrolling
            a.setInterpolator(new SplineInterpolator(0f,.02f,0f,1f));
            // finally start the timer
            a.start();
        }
    }
    
}
