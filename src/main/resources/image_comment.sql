-- Table: public.image_comment

-- DROP TABLE IF EXISTS public.image_comment;

CREATE TABLE IF NOT EXISTS public.image_comment
(
    id bigint NOT NULL GENERATED ALWAYS AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1 ),
    image_id bigint NOT NULL,
    comment_id bigint NOT NULL,
    CONSTRAINT image_comment_pkey PRIMARY KEY (id),
    CONSTRAINT comments_fkey FOREIGN KEY (comment_id)
        REFERENCES public.comments (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT images_fkey FOREIGN KEY (image_id)
        REFERENCES public.images (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.image_comment
    OWNER to admin;