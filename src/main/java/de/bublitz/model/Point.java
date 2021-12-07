package de.bublitz.model;

import lombok.Data;

@Data
public class Point {
    private int x;
    private int y;

    /**
     * Berechnet die Distanz
     *
     * @param p Punkt
     * @return Die Distanz
     */
    public double distance(Point p) {
        return Math.sqrt(Math.pow(this.x - p.getX(), 2) + Math.pow(this.y - p.getY(), 2));
    }
}
