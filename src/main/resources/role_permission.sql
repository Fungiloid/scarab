-- Table: public.role_permission

DROP TABLE IF EXISTS public.role_permission CASCADE;

CREATE TABLE IF NOT EXISTS public.role_permission
(
    id UUID DEFAULT uuid_generate_v4(),
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    CONSTRAINT role_permission_pkey PRIMARY KEY (id),
    CONSTRAINT role_fkey FOREIGN KEY (role_id)
        REFERENCES public.roles (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT permission_fkey FOREIGN KEY (permission_id)
        REFERENCES public.permissions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.role_permission
    OWNER to admin;