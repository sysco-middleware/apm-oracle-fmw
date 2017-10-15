xquery version "1.0" encoding "utf-8";

(:: OracleAnnotationVersion "1.0" ::)

declare namespace ns1="http://middleware.sysco.no/apm/tracing/schema";
(:: import schema at "../schema/Trace.xsd" ::)

declare variable $messageId as xs:string external;
declare variable $operationName as xs:string external;

declare function local:func($messageId as xs:string, 
                            $operationName as xs:string) 
                            as element() (:: schema-element(ns1:endSpan) ::) {
    <ns1:endSpan>
        <ns1:messageId>{fn:data($messageId)}</ns1:messageId>
        <ns1:operationName>{fn:data($operationName)}</ns1:operationName>
        <ns1:instant>{fn:current-dateTime()}</ns1:instant>
    </ns1:endSpan>
};

local:func($messageId, $operationName)