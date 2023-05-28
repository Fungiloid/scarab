CREATE TABLE public.images
(
    id bigint NOT NULL,
    created timestamp with time zone NOT NULL,
    location character varying,
    name character varying,
    description character varying,
    PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS public.images
    OWNER to admin;

CREATE SEQUENCE public.images_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public.images_id_seq OWNER TO admin;

ALTER SEQUENCE public.images_id_seq OWNED BY public.images.id;

ALTER TABLE ONLY public.images ALTER COLUMN id SET DEFAULT nextval('public.images_id_seq'::regclass);

ALTER TABLE ONLY public.images
    ADD CONSTRAINT images_pkey PRIMARY KEY (id);