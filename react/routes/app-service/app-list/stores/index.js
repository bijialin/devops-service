import React, { createContext, useContext, useEffect, useMemo } from 'react';
import { inject } from 'mobx-react';
import { injectIntl } from 'react-intl';
import { DataSet } from 'choerodon-ui/pro';
import useStore from '../../stores/useStore';
import ListDataSet from '../../stores/ListDataSet';
import ImportDataSet from './ImportDataSet';
import ImportTableDataSet from './ImportTableDataSet';
import getTablePostData from '../../../../utils/getTablePostData';
import selectedDataSet from './SelectedDataSet';

const Store = createContext();

export function useAppServiceStore() {
  return useContext(Store);
}

export const StoreProvider = injectIntl(inject('AppState')(
  (props) => {
    const {
      AppState: { currentMenuType: { projectId, organizationId } },
      intl: { formatMessage },
      children,
    } = props;
    const intlPrefix = 'c7ncd.appService';
    const AppStore = useStore();
    const listDs = useMemo(() => new DataSet(ListDataSet(intlPrefix, formatMessage, projectId)), [formatMessage, projectId]);
    const importTableDs = useMemo(() => new DataSet(ImportTableDataSet(intlPrefix, formatMessage, projectId)), [formatMessage, projectId]);
    const selectedDs = useMemo(() => new DataSet(selectedDataSet(intlPrefix, formatMessage, projectId)), [projectId]);
    const importDs = useMemo(() => new DataSet(ImportDataSet(intlPrefix, formatMessage, projectId, selectedDs)), [formatMessage, projectId, selectedDs]);

    useEffect(() => {
      listDs.transport.read = ({ data }) => {
        const postData = getTablePostData(data);

        return {
          url: `/devops/v1/projects/${projectId}/app_service/page_by_options`,
          method: 'post',
          data: postData,
        };
      };
      listDs.query();
    }, [projectId]);

    useEffect(() => {
      AppStore.judgeRole(organizationId, projectId);
    }, [organizationId, projectId]);

    const value = {
      ...props,
      prefixCls: 'c7ncd-appService',
      intlPrefix,
      listDs,
      importDs,
      importTableDs,
      AppStore,
      selectedDs,
    };
    return (
      <Store.Provider value={value}>
        {children}
      </Store.Provider>
    );
  },
));