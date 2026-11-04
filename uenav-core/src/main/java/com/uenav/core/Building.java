package com.uenav.core;

/** One row of the Building table, as sent in the snapshot. mapX/mapY and imagePath may be null. */
public record Building(int id, String name, String category, String description,
                       Integer mapX, Integer mapY, String imagePath) { }
