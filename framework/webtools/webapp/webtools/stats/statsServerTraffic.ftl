<#assign library=chartLibrary!"foundation"/>
<#assign currData=rewrapMap(chartData, "raw-simple")/>
<#assign fieldIdNum=fieldIdNum!0/>

<@section title=title!"">
  <#if currData?has_content>
    <@chart type=chartType library=library>
      <#list mapKeys(currData) as key>
        <#assign date = key?date/>
          <@chartdata value=((currData[key].count)!0) title=key/>
      </#list>
    </@chart>
  <#else>
    <@commonMsg type="result-norecord" />
  </#if>
</@section>