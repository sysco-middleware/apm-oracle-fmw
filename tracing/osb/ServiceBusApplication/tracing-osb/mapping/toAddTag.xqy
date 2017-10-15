xquery version "1.0" encoding "utf-8";

(:: OracleAnnotationVersion "1.0" ::)

declare namespace ns1="http://middleware.sysco.no/apm/tracing/schema";
(:: import schema at "../schema/Trace.xsd" ::)

declare variable $key as xs:string external;
declare variable $value as xs:string external;
declare variable $tags as element() (:: schema-element(ns1:tags) ::) external;

declare function local:func($key as xs:string, 
                            $value as xs:string, 
                            $tags as element()? (:: schema-element(ns1:tags) ::)) 
                            as element() (:: schema-element(ns1:tags) ::) {
    <ns1:tags>
        {
            if(fn:empty($tags)) then
              ()
            else
              for $tag in $tags/ns1:tag
              return $tag
        }
        <ns1:tag>
            <ns1:key>{fn:data($key)}</ns1:key>
            <ns1:value>{fn:data($value)}</ns1:value>
        </ns1:tag>
    </ns1:tags>
};

local:func($key, $value, $tags)