xquery version "1.0" encoding "utf-8";

(:: OracleAnnotationVersion "1.0" ::)

declare namespace ns1="http://middleware.sysco.no/apm/metrics/schema";
(:: import schema at "../schema/Metrics.xsd" ::)

declare variable $metricName as xs:string external;
declare variable $metricDescription as xs:string external;
declare variable $metricValue as xs:double? external;
declare variable $tags as element() (:: schema-element(ns1:tags) ::) external;

declare function local:func($metricName as xs:string, 
                            $metricDescription as xs:string, 
                            $metricValue as xs:double?, 
                            $tags as element() (:: schema-element(ns1:tags) ::)) 
                            as element() (:: schema-element(ns1:metric) ::) {
    <ns1:metric>
        <ns1:componentName>osb</ns1:componentName>
        <ns1:metricName>{fn:data($metricName)}</ns1:metricName>
        <ns1:metricValue>{fn:data($metricValue)}</ns1:metricValue>
        <ns1:metricDescription>{fn:data($metricDescription)}</ns1:metricDescription>
        <ns1:tags>
            {
                for $tag in $tags/ns1:tag
                return
                  $tag
            }
        </ns1:tags>
    </ns1:metric>
};

local:func($metricName, $metricDescription, $metricValue, $tags)
