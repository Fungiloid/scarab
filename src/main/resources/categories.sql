CREATE TABLE public.categories (
    id bigint NOT NULL,
    created timestamp with time zone NOT NULL,
    name character varying
);

ALTER TABLE public.categories OWNER TO admin;

CREATE SEQUENCE public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.categories_id_seq OWNER TO admin;

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);


ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


