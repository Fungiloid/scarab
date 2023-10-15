-- Table: public.group_role

DROP TABLE IF EXISTS public.group_role CASCADE;

CREATE TABLE IF NOT EXISTS public.group_role
(
    id UUID DEFAULT uuid_generate_v4(),
    group_id uuid NOT NULL,
    role_id uuid NOT NULL,
    CONSTRAINT group_role_pkey PRIMARY KEY (id),
    CONSTRAINT group_fkey FOREIGN KEY (group_id)
        REFERENCES public.groups (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT role_fkey FOREIGN KEY (role_id)
        REFERENCES public.roles (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.group_role
    OWNER to admin;