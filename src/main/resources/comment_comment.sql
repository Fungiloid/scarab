-- Table: public.comment_comment

-- DROP TABLE IF EXISTS public.comment_comment;

CREATE TABLE IF NOT EXISTS public.comment_comment
(
    id bigint NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1 ),
    parent_id bigint NOT NULL,
    child_id bigint,
    CONSTRAINT comment_comment_pkey PRIMARY KEY (id),
    CONSTRAINT child_fkey FOREIGN KEY (child_id)
        REFERENCES public.comments (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT parent_fkey FOREIGN KEY (parent_id)
        REFERENCES public.comments (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.comment_comment
    OWNER to admin;