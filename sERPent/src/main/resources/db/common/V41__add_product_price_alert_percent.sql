-- V41__add_product_price_alert_percent.sql
--
-- LA TOLERANCIA DE PRECIO DE CADA PRODUCTO, PARA EL AVISO DE "PRECIO FUERA DE RANGO".
--
-- QUÉ PROBLEMA RESUELVE
-- El único control que había sobre un precio tipeado era el techo de importe, de siete dígitos.
-- Ese techo atrapa un disparate ($ 99.999.999) y no atrapa el error que de verdad pasa: un cero
-- de más. Poner 17.000 donde iba 1.700 pasa el techo sin despeinarse, y a partir de ahí todas
-- las ventas de ese producto salen diez veces más caras hasta que alguien lo note.
--
-- POR QUÉ UNA COLUMNA POR PRODUCTO Y NO UN NÚMERO GLOBAL
-- Porque los productos no se mueven igual. En una pollería el precio del pollo cambia todas las
-- semanas y el de una gaseosa no se toca en meses. Una tolerancia única o grita todos los días
-- en los que varían —y un aviso que grita siempre se vuelve invisible— o no salta nunca en los
-- que no. El esquema ya tiene este mismo patrón en reorder_point: un umbral por producto que,
-- cuando está vacío, cae en el criterio general.
--
-- ANULABLE A PROPÓSITO, Y SIN DEFAULT EN LA BASE
-- NULL quiere decir "usá el valor por omisión", que vive en el código (PriceAlertPolicy) y no
-- acá. Dos motivos:
--   1. Los productos que ya existen quedan en NULL y funcionan sin que nadie configure nada.
--   2. Cambiar el valor por omisión pasa a ser un despliegue y no una migración sobre una base
--      con datos. Un número que va a moverse con la experiencia no se congela en el esquema.
--
-- NUMERIC(5,2) da hasta 999,99 %, que alcanza para decir "avisame solo si se va al triple" sin
-- dejar escribir cualquier cosa. El CHECK exige que sea positivo: una tolerancia de 0 % avisaría
-- ante cualquier cambio, aunque sea de un centavo, y eso no es un aviso, es ruido.
--
-- ES SEGURA SI FALLA: agregar una columna anulable no puede perder un dato. Si esta migración no
-- se aplicara, lo único que pasa es que el aviso no existe.

ALTER TABLE products ADD COLUMN price_alert_percent NUMERIC(5,2);

ALTER TABLE products
    ADD CONSTRAINT ck_products_price_alert_percent_positive
    CHECK (price_alert_percent IS NULL OR price_alert_percent > 0);
