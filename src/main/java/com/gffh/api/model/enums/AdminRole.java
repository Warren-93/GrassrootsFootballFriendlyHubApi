package com.gffh.api.model.enums;

/**
 * The two levels of platform admin. A SUPER_ADMIN can do everything an ADMIN
 * can, and also manages the admin accounts and approves the permanent
 * deletes an ADMIN asks for.
 */
public enum AdminRole { SUPER_ADMIN, ADMIN }
