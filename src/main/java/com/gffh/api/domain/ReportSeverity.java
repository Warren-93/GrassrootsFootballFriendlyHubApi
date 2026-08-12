package com.gffh.api.domain;

/** SAFEGUARDING reports default to the top of the queue and cannot be bulk-actioned (ADM-04). */
public enum ReportSeverity { LOW, MEDIUM, HIGH, SAFEGUARDING }
