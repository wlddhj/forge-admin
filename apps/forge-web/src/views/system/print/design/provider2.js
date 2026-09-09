import { hiprint } from "vue-plugin-hiprint";
export const provider2 = function (options) {
  console.log(options);
  var addElementTypes = function (context) {
    context.removePrintElementTypes("providerModule2");
    context.addPrintElementTypes("providerModule2", [
      new hiprint.PrintElementTypeGroup("表格/其他", [
        {
          tid: "providerModule2.orderItemList",
          title: "订单表格",
          type: "table",
          options: {
            field: "orderItemList"
          },
          columns: [
            [
              { title: "序号",  align: "center", field: "seqNo", width: 30 },
              { title: "代码",  align: "left", field: "skuCode", width: 75,tableSummaryTitle: true,tableSummaryText: "合计" },
              { title: "品名",  align: "left", field: "goodsName", width: 122 },
              { title: "规格",  align: "left", field: "spec", width: 67 },
              { title: "单位",  align: "center", field: "productUnitName", width: 33 },
              { title: "数量",  align: "right", field: "num", width: 36, formatter2: "function (value, row, index, options) { return formatNumber(value);}",tableSummaryTitle: false,tableSummary: "sum",tableSummaryFormatter: "function (column,fieldPageData,tableData,options) {\nconst total = fieldPageData.reduce((accumulator, currentValue) => {\n  return accumulator + currentValue;\n}, 0);\nreturn '<td style=\"text-align:right\">'+formatNumber(total)+'</td>'\n}" },
              { title: "单价",  align: "right", field: "price", width: 39, formatter2: "function (value, row, index, options) { return formatNumber(value);}"},
              { title: "金额",  align: "right", field: "amount", width: 52,formatter2: "function (value, row, index, options) { return formatNumber(value);}", tableSummaryTitle: false,tableSummary: "sum",tableSummaryFormatter: "function (column,fieldPageData,tableData,options) {\nconst total = fieldPageData.reduce((accumulator, currentValue) => {\n  return accumulator + currentValue;\n}, 0);\nreturn '<td style=\"text-align:right\">'+formatNumber(total)+'</td>'\n}" },
              { title: "库位",  align: "center", field: "slotNo", width: 40 },
              { title: "备注",  align: "left", field: "remark", width: 58 },
            ],
          ]
        },
        {
          tid: "providerModule2.table2",
          title: "订单表格-双表头",
          type: "table",
          options: {
            field: "table2",
            groupFooterFormatter: function(groupData, options) {
              console.log("groupData", groupData, options);
              // return  t6;
              // const sum = groupData.rows.reduce((acc, row) => acc + row.count, 0);
              return '按人员分组'
            },
            groupFieldsFormatter: function(type,options,data){ return ["name"] },
          },
          columns: [
            [
              {
                width: 70,
                title: "行号",
                field: "id",
                rowspan: 2,
                colspan: 1
              },
              {
                width: 100,
                title: "人员信息",
                rowspan: 1,
                colspan: 2
              },
              {
                width: 100,
                title: "销售统计",
                rowspan: 1,
                colspan: 2
              }
            ],
            [
              {
                width: 120,
                title: "姓名",
                field: "name",
                align: "left"
              },
              {
                width: 120,
                title: "性别",
                field: "gender",
              },
              {
                width: 120,
                title: "销售数量",
                field: "count",
              },
              {
                width: 120,
                title: "销售金额",
                field: "amount",
              }
            ]
          ]
        },
      ])
    ]);
  };
  return {
    addElementTypes: addElementTypes,
  };
};
